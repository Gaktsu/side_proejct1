package com.iot.project.Analysis;

import com.iot.project.AnalysisCandidate.AnalysisCandidate;
import com.iot.project.AnalysisCandidate.AnalysisCandidateRepository;
import com.iot.project.AnalysisCandidate.InvalidReason;
import com.iot.project.QualityCase.CaseStatus;
import com.iot.project.QualityCase.QualityCase;
import com.iot.project.QualityCase.QualityCaseDocument;
import com.iot.project.QualityCase.QualityCaseRepository;
import com.iot.project.embedding.EmbeddingClient;
import com.iot.project.qdrant.QdrantClient;
import com.iot.project.qdrant.ScoredPoint;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

// PENDING 분석을 폴링해 쿼리 임베딩 → Qdrant top-k → MySQL 검증 → 후보 저장을 트랜잭션 밖에서 실행한다
// 인덱싱 처리기와 스레드를 나눠 인덱싱 대기열 뒤에서 기다리지 않는다 (임베딩 계산 자체는 EmbeddingClient 잠금으로 1건씩)
// 사람이 기다리는 작업이라 자동 재시도 없이 실패하면 바로 FAILED (다시 요청하면 새로 분석)
@Slf4j
@Component
public class AnalysisWorker {

    // 부정 조건을 못 가리는 임베딩 대신 JEV/LLM이 고를 수 있게 넉넉히 가져온다 (/similar-cases와 같은 값)
    static final int TOP_K = 10;

    private final AnalysisRepository analysisRepository;
    private final AnalysisCandidateRepository candidateRepository;
    private final QualityCaseRepository qualityCaseRepository;
    private final TransactionTemplate transactionTemplate;
    private final EmbeddingClient embeddingClient;
    private final QdrantClient qdrantClient;
    private final boolean enabled;
    private final long pollDelayMs;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    // 폴링 주기만큼 시작이 늦어질 수 있어 인덱싱(5초)보다 짧게 (검색 3초 목표 중 최대 1초)
    public AnalysisWorker(AnalysisRepository analysisRepository, AnalysisCandidateRepository candidateRepository,
                          QualityCaseRepository qualityCaseRepository, TransactionTemplate transactionTemplate,
                          EmbeddingClient embeddingClient, QdrantClient qdrantClient,
                          @Value("${indexing.worker.enabled:true}") boolean enabled,
                          @Value("${analysis.poll-delay-ms:1000}") long pollDelayMs) {
        this.analysisRepository = analysisRepository;
        this.candidateRepository = candidateRepository;
        this.qualityCaseRepository = qualityCaseRepository;
        this.transactionTemplate = transactionTemplate;
        this.embeddingClient = embeddingClient;
        this.qdrantClient = qdrantClient;
        this.enabled = enabled;
        this.pollDelayMs = pollDelayMs;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled) return;
        int recovered = recoverInterrupted();
        if (recovered > 0) log.info("검색 중 멈춘 분석 {}건을 PENDING으로 되돌림", recovered);
        executor.scheduleWithFixedDelay(this::pollSafely, pollDelayMs, pollDelayMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }

    // 기동 시점의 SEARCHING은 이전 프로세스가 처리하다 멈춘 것 (인스턴스 1대 전제)
    public int recoverInterrupted() {
        return analysisRepository.resetSearching();
    }

    // 예외가 밖으로 나가면 scheduleWithFixedDelay가 이후 실행을 멈추므로 여기서 막는다
    private void pollSafely() {
        try {
            while (pollOnce()) {
                // 쌓인 분석은 다음 주기를 기다리지 않고 이어서 처리
            }
        } catch (RuntimeException e) {
            log.error("분석 폴링 실패", e);
        }
    }

    // PENDING 분석 하나를 선점해 처리한다. 선점할 분석이 없으면 false
    public boolean pollOnce() {
        Optional<Analysis> next = analysisRepository.findNextRunnable();
        if (next.isEmpty()) return false;
        Long analysisId = next.get().getId();
        if (analysisRepository.claim(analysisId)) {
            try {
                process(analysisId);
            } catch (RuntimeException e) {
                log.error("분석 실패 analysisId={}", analysisId, e);
                analysisRepository.fail(analysisId, e.toString());
            }
        }
        return true;
    }

    private void process(long analysisId) {
        Analysis analysis = analysisRepository.findById(analysisId).orElseThrow();
        // 실행 시점의 사례로 검색한다 (요청 뒤 수정됐으면 최신 내용). 그사이 삭제·해결됐어도 확인하지 않는다
        // (삭제는 조회 API가 404로 처리, 해결은 결과에 영향 없음). 삭제된 사례도 읽는 쿼리
        QualityCase target = qualityCaseRepository.findWithRefsById(analysis.getQualityCase().getId()).orElseThrow();
        String queryText = QualityCaseDocument.toQueryText(target);
        log.info("분석 시작 analysisId={}, caseId={}, 쿼리 텍스트:\n{}", analysisId, target.getId(), queryText);

        long start = System.nanoTime();
        float[] vector = embeddingClient.embedQuery(queryText);
        long embedded = System.nanoTime();
        List<ScoredPoint> points = qdrantClient.search(vector, TOP_K);
        long searched = System.nanoTime();

        // Qdrant가 searchable=true로 걸렀어도 반영 전 구간이 있어 MySQL 현재 상태로 다시 본다 (삭제된 사례도 읽음)
        Map<Long, QualityCase> cases = qualityCaseRepository.findAllById(points.stream().map(ScoredPoint::id).toList())
                .stream().collect(Collectors.toMap(QualityCase::getId, Function.identity()));
        List<AnalysisCandidate> candidates = new ArrayList<>();
        List<String> logLines = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            ScoredPoint point = points.get(i);
            int rank = i + 1;
            QualityCase candidate = cases.get(point.id());
            if (candidate == null) {
                // Qdrant에만 남은 벡터: FK 때문에 저장할 수 없다
                logLines.add(String.format("  %d위 caseId=%d score=%.6f MySQL에 없음(저장 안 함)", rank, point.id(), point.score()));
                continue;
            }
            InvalidReason reason = invalidReason(candidate, point);
            logLines.add(String.format("  %d위 caseId=%d score=%.6f invalidReason=%s", rank, point.id(), point.score(), reason));
            candidates.add(new AnalysisCandidate(analysis, candidate, rank,
                    BigDecimal.valueOf(point.score()).setScale(6, RoundingMode.HALF_UP), reason));
        }

        boolean anyUsable = candidates.stream().anyMatch(c -> c.getInvalidReason() == null);
        AnalysisStatus result = anyUsable ? AnalysisStatus.CANDIDATES_READY : AnalysisStatus.NO_CANDIDATES;
        // 후보와 최종 상태를 한 트랜잭션으로: 사이에 종료돼도 후보가 일부만 남지 않는다 (SEARCHING으로 남아 기동 시 다시)
        transactionTemplate.executeWithoutResult(status -> {
            candidateRepository.saveAll(candidates);
            analysisRepository.finish(analysisId, result);
        });
        log.info("분석 완료 analysisId={}, {}, 후보 {}건(저장 {}건), 임베딩 {}ms, search {}ms, 저장 {}ms\n{}",
                analysisId, result, points.size(), candidates.size(), (embedded - start) / 1_000_000,
                (searched - embedded) / 1_000_000, (System.nanoTime() - searched) / 1_000_000,
                String.join("\n", logLines));
    }

    // 삭제 → 미해결 → 버전 순서로 보고 처음 걸린 하나만. 모두 통과하면 null(근거로 사용 가능)
    private static InvalidReason invalidReason(QualityCase candidate, ScoredPoint point) {
        if (candidate.isDeleted()) return InvalidReason.DELETED;
        if (candidate.getStatus() != CaseStatus.RESOLVED) return InvalidReason.NOT_RESOLVED;
        if (point.payload().sourceVersion() != candidate.getSourceVersion()) return InvalidReason.STALE_VERSION;
        return null;
    }
}
