package com.iot.project.IndexingJob;

import com.iot.project.QualityCase.CaseStatus;
import com.iot.project.QualityCase.QualityCase;
import com.iot.project.QualityCase.QualityCaseRepository;
import com.iot.project.qdrant.QdrantClient;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// search_visibility_job을 폴링해 Qdrant searchable을 MySQL 현재 상태에 맞춘다
// searchable을 바꾸는 곳은 여기 하나뿐이고 스레드도 하나라 job이 들어온 순서대로 끝난다 → 마지막 job이 최신 상태로 맞춤
// 임베딩(IndexingWorker)과 스레드를 나눠, 수 초짜리 임베딩 뒤에서 기다리지 않는다
// 검색은 이 결과만 믿지 않고 MySQL로 다시 거른다 (반영 전·재시도 중·FAILED 구간)
@Slf4j
@Component
public class SearchVisibilityWorker {

    private final SearchVisibilityJobRepository jobRepository;
    private final QualityCaseRepository qualityCaseRepository;
    private final QdrantClient qdrantClient;
    private final boolean enabled;
    private final long pollDelayMs;
    // IndexingWorker와 같은 재시도 간격 (길이 + 1 = 최대 시도 횟수)
    private final long[] retryDelaysSeconds;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    // 폴링 주기가 곧 삭제·취소 후 검색 결과가 줄어드는 구간이라 인덱싱(5초)보다 짧게
    public SearchVisibilityWorker(SearchVisibilityJobRepository jobRepository, QualityCaseRepository qualityCaseRepository,
                                  QdrantClient qdrantClient,
                                  @Value("${indexing.worker.enabled:true}") boolean enabled,
                                  @Value("${indexing.visibility-poll-delay-ms:1000}") long pollDelayMs,
                                  @Value("${indexing.retry-delays-seconds:30,60,120,240}") long[] retryDelaysSeconds) {
        this.jobRepository = jobRepository;
        this.qualityCaseRepository = qualityCaseRepository;
        this.qdrantClient = qdrantClient;
        this.enabled = enabled;
        this.pollDelayMs = pollDelayMs;
        this.retryDelaysSeconds = retryDelaysSeconds;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled) return;
        int recovered = recoverInterrupted();
        if (recovered > 0) log.info("검색 노출 반영 중 멈춘 job {}건을 PENDING으로 되돌림", recovered);
        executor.scheduleWithFixedDelay(this::pollSafely, pollDelayMs, pollDelayMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }

    public int recoverInterrupted() {
        return jobRepository.resetProcessing();
    }

    private void pollSafely() {
        try {
            while (pollOnce()) {
                // 쌓인 job은 다음 주기를 기다리지 않고 이어서 처리
            }
        } catch (RuntimeException e) {
            log.error("검색 노출 폴링 실패", e);
        }
    }

    // PENDING job 하나를 선점해 한 번 시도한다. 선점할 job이 없으면 false
    public boolean pollOnce() {
        Optional<SearchVisibilityJob> next = jobRepository.findNextRunnable();
        if (next.isEmpty()) return false;
        Long jobId = next.get().getId();
        if (jobRepository.claim(jobId)) {
            attempt(jobId, 1);
        }
        return true;
    }

    private void attemptSafely(long jobId, int attempt) {
        try {
            attempt(jobId, attempt);
        } catch (RuntimeException e) {
            log.error("검색 노출 재시도 실패 jobId={}", jobId, e);
        }
    }

    // 실패하면 PROCESSING으로 둔 채 스레드를 놓아주고 나중에 다시 시도한다
    private void attempt(long jobId, int attempt) {
        try {
            process(jobId);
            jobRepository.complete(jobId);
        } catch (RuntimeException e) {
            if (attempt <= retryDelaysSeconds.length) {
                long delay = retryDelaysSeconds[attempt - 1];
                log.warn("검색 노출 반영 실패 jobId={}, {}회차, {}초 뒤 재시도: {}", jobId, attempt, delay, e.toString());
                executor.schedule(() -> attemptSafely(jobId, attempt + 1), delay, TimeUnit.SECONDS);
                return;
            }
            log.error("검색 노출 반영 최종 실패 jobId={}, {}회차", jobId, attempt, e);
            jobRepository.fail(jobId, e.toString());
        }
    }

    // 목표 값은 job이 아니라 지금의 MySQL 상태로 정한다 (삭제된 사례도 읽는다)
    private void process(long jobId) {
        SearchVisibilityJob job = jobRepository.findById(jobId).orElseThrow();
        QualityCase qualityCase = qualityCaseRepository.findById(job.getQualityCase().getId()).orElseThrow();
        boolean searchable = qualityCase.getStatus() == CaseStatus.RESOLVED && !qualityCase.isDeleted();
        qdrantClient.setSearchable(qualityCase.getId(), searchable, qualityCase.getSourceVersion());
        log.info("검색 노출 반영 jobId={}, caseId={}, searchable={}, v{}", jobId, qualityCase.getId(), searchable,
                qualityCase.getSourceVersion());
    }
}
