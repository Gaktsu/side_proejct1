package com.iot.project.IndexingJob;

import com.iot.project.QualityCase.QualityCase;
import com.iot.project.QualityCase.QualityCaseDocument;
import com.iot.project.QualityCase.QualityCaseRepository;
import com.iot.project.embedding.EmbeddingClient;
import com.iot.project.embedding.EmbeddingInputTooLongException;
import com.iot.project.qdrant.QdrantClient;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// 해결·수정 커밋 뒤 indexing_job을 폴링해 임베딩 → Qdrant upsert를 트랜잭션 밖에서 실행한다
// 폴링과 재시도를 스레드 하나에서 돌려 임베딩은 한 번에 하나 (ONNX 메모리 수위 유지)
// ponytail: 단일 스레드·단일 인스턴스 전제, 처리량이 부족하면 스레드 추가 또는 인스턴스 분리(소유자 표시 필요)
@Slf4j
@Component
public class IndexingWorker {

    private final IndexingJobRepository jobRepository;
    private final SearchVisibilityJobRepository visibilityJobRepository;
    private final QualityCaseRepository qualityCaseRepository;
    private final TransactionTemplate transactionTemplate;
    private final EmbeddingClient embeddingClient;
    private final QdrantClient qdrantClient;
    private final boolean enabled;
    private final long pollDelayMs;
    // 실패 후 다시 시도하기까지 대기(초). 길이 + 1 = 최대 시도 횟수 (기본 최초 포함 5회, 합계 7.5분)
    private final long[] retryDelaysSeconds;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    public IndexingWorker(IndexingJobRepository jobRepository, SearchVisibilityJobRepository visibilityJobRepository,
                          QualityCaseRepository qualityCaseRepository, TransactionTemplate transactionTemplate,
                          EmbeddingClient embeddingClient, QdrantClient qdrantClient,
                          @Value("${indexing.worker.enabled:true}") boolean enabled,
                          @Value("${indexing.poll-delay-ms:5000}") long pollDelayMs,
                          @Value("${indexing.retry-delays-seconds:30,60,120,240}") long[] retryDelaysSeconds) {
        this.jobRepository = jobRepository;
        this.visibilityJobRepository = visibilityJobRepository;
        this.qualityCaseRepository = qualityCaseRepository;
        this.transactionTemplate = transactionTemplate;
        this.embeddingClient = embeddingClient;
        this.qdrantClient = qdrantClient;
        this.enabled = enabled;
        this.pollDelayMs = pollDelayMs;
        this.retryDelaysSeconds = retryDelaysSeconds;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled) return;
        int recovered = recoverInterrupted();
        if (recovered > 0) log.info("인덱싱 처리 중 멈춘 단계 {}건을 PENDING으로 되돌림", recovered);
        executor.scheduleWithFixedDelay(this::pollSafely, pollDelayMs, pollDelayMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }

    // 기동 시점의 PROCESSING은 이전 프로세스가 처리하다 멈춘 것 (인스턴스 1대 전제)
    public int recoverInterrupted() {
        return jobRepository.resetProcessing();
    }

    // 예외가 밖으로 나가면 scheduleWithFixedDelay가 이후 실행을 멈추므로 여기서 막는다
    private void pollSafely() {
        try {
            while (pollOnce()) {
                // 쌓인 job은 다음 주기를 기다리지 않고 이어서 처리
            }
        } catch (RuntimeException e) {
            log.error("인덱싱 폴링 실패", e);
        }
    }

    // 실행 가능한 job 하나를 선점해 한 번 시도한다. 선점한 job이 없으면 false
    public boolean pollOnce() {
        Optional<IndexingJob> next = jobRepository.findNextRunnable();
        if (next.isEmpty()) return false;
        IndexingJob job = next.get();
        boolean claimed = job.getEmbeddingStatus() == IndexingStatus.PENDING
                ? jobRepository.claimEmbedding(job.getId())
                : jobRepository.claimQdrant(job.getId());
        if (claimed) {
            attempt(job.getId(), 1);
        }
        return true;
    }

    private void attemptSafely(long jobId, int attempt) {
        try {
            attempt(jobId, attempt);
        } catch (RuntimeException e) {
            log.error("인덱싱 재시도 실패 jobId={}", jobId, e);
        }
    }

    // 실패하면 job을 PROCESSING으로 둔 채 스레드를 놓아주고 나중에 다시 시도한다 (다른 job이 기다리지 않게)
    private void attempt(long jobId, int attempt) {
        IndexingJob job = jobRepository.findById(jobId).orElseThrow();
        boolean embeddingPhase = job.getEmbeddingStatus() == IndexingStatus.PROCESSING;
        try {
            process(job, embeddingPhase);
        } catch (RuntimeException e) {
            // 토큰 초과는 다시 해도 같은 결과 (해결·수정 때 동기 검사하므로 사실상 발생하지 않음)
            boolean retryable = !(e instanceof EmbeddingInputTooLongException);
            if (retryable && attempt <= retryDelaysSeconds.length) {
                long delay = retryDelaysSeconds[attempt - 1];
                log.warn("인덱싱 실패 jobId={}, {}회차, {}초 뒤 재시도: {}", jobId, attempt, delay, e.toString());
                executor.schedule(() -> attemptSafely(jobId, attempt + 1), delay, TimeUnit.SECONDS);
                return;
            }
            log.error("인덱싱 최종 실패 jobId={}, {}회차", jobId, attempt, e);
            // 한 시도 안에서 임베딩 → Qdrant로 넘어가므로 시작 단계로는 실패 단계를 알 수 없다.
            // 조건부 UPDATE라 둘 다 호출해도 지금 PROCESSING인 단계만 FAILED가 된다
            jobRepository.failEmbedding(jobId, e.toString());
            jobRepository.failQdrant(jobId, e.toString());
        }
    }

    // embeddingPhase=false: Qdrant 단계에서 다시 시작한 경우. 벡터를 보존하지 않으므로 다시 임베딩한다
    private void process(IndexingJob job, boolean embeddingPhase) {
        long jobId = job.getId();
        long caseId = job.getQualityCase().getId();
        QualityCase qualityCase = qualityCaseRepository.findWithRefsById(caseId).orElseThrow();
        // 처리 전에 사례가 수정됐으면 옛 버전 job → 건너뛴다 (새 버전 job이 따로 있음)
        if (qualityCase.getSourceVersion() != job.getSourceVersion().intValue()) {
            log.info("인덱싱 건너뜀 jobId={}, job 버전 {} ≠ 사례 버전 {}", jobId, job.getSourceVersion(), qualityCase.getSourceVersion());
            if (embeddingPhase) {
                jobRepository.supersedeEmbedding(jobId);
            } else {
                jobRepository.supersedeQdrant(jobId);
            }
            return;
        }

        long start = System.nanoTime();
        float[] vector = embeddingClient.embedDocument(QualityCaseDocument.toText(qualityCase));
        long embedded = System.nanoTime();
        if (embeddingPhase) {
            jobRepository.completeEmbedding(jobId);
            jobRepository.claimQdrant(jobId);
        }

        // 임베딩하는 동안 수정됐을 수 있어 upsert 직전에 한 번 더 비교한다
        // 삭제 여부는 보지 않는다: 삭제된 사례도 벡터는 올려 둬야 복구 때 다시 쓸 수 있고, 노출은 SearchVisibilityWorker가 정한다
        if (qualityCaseRepository.findSourceVersionById(caseId) != job.getSourceVersion().intValue()) {
            log.info("인덱싱 건너뜀(upsert 전) jobId={}", jobId);
            jobRepository.supersedeQdrant(jobId);
            return;
        }
        qdrantClient.upsert(caseId, vector, job.getSourceVersion());
        // upsert는 searchable=false라 노출 job이 있어야 검색에 나타난다. 완료 기록과 한 트랜잭션으로 묶어
        // 사이에 종료돼도 job이 빠지지 않게 한다 (Qdrant 단계가 PROCESSING으로 남아 기동 시 다시 처리)
        transactionTemplate.executeWithoutResult(status -> {
            jobRepository.completeQdrant(jobId);
            visibilityJobRepository.save(new SearchVisibilityJob(qualityCaseRepository.getReferenceById(caseId)));
        });
        log.info("인덱싱 완료 jobId={}, caseId={}, v{}, 임베딩 {}ms, upsert {}ms", jobId, caseId, job.getSourceVersion(),
                (embedded - start) / 1_000_000, (System.nanoTime() - embedded) / 1_000_000);
    }
}
