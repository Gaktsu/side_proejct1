package com.iot.project.Analysis;

import com.iot.project.QualityCase.ResolveTestData;
import com.iot.project.embedding.EmbeddingClient;
import com.iot.project.qdrant.PointPayload;
import com.iot.project.qdrant.QdrantClient;
import com.iot.project.qdrant.ScoredPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 처리기는 자기 트랜잭션으로 커밋하므로 @Transactional 없이 직접 넣고 지운다
// 스케줄은 꺼져 있고(test config) pollOnce를 직접 호출한다. 실제 모델·Qdrant 대신 mock
@SpringBootTest
class AnalysisWorkerTest {

    @Autowired
    AnalysisWorker worker;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    EmbeddingClient embeddingClient;

    @MockitoBean
    QdrantClient qdrantClient;

    private final float[] vector = new float[1024];
    private Long targetId;
    private Long analysisId;

    @BeforeEach
    void setUp() {
        AnalysisTestData.insertMasters(jdbcTemplate);
        targetId = AnalysisTestData.insertCase(jdbcTemplate, "TEST-QC-W-TARGET", false, 0, false);
        analysisId = AnalysisTestData.insertAnalysis(jdbcTemplate, targetId, "PENDING");
        when(embeddingClient.embedQuery(anyString())).thenReturn(vector);
    }

    @AfterEach
    void cleanUp() {
        ResolveTestData.deleteAll(jdbcTemplate);
    }

    private Map<String, Object> analysis() {
        return jdbcTemplate.queryForMap("SELECT analysis_status, error_message FROM analysis WHERE id = ?", analysisId);
    }

    private List<Map<String, Object>> candidates() {
        return jdbcTemplate.queryForList("""
                SELECT candidate_quality_case_id, qdrant_rank, invalid_reason, is_null FROM analysis_candidate
                WHERE analysis_id = ? ORDER BY qdrant_rank""", analysisId);
    }

    private static ScoredPoint point(Long id, double score, int version) {
        return new ScoredPoint(id, score, new PointPayload(version, true));
    }

    @Test
    void 후보를_전부_저장하고_근거로_못쓰는_후보는_사유를_남긴다() {
        Long valid = AnalysisTestData.insertCase(jdbcTemplate, "TEST-QC-W-VALID", true, 1, false);
        // 삭제 + 미해결이 겹치면 먼저 확인하는 DELETED 하나만
        Long deletedOpen = AnalysisTestData.insertCase(jdbcTemplate, "TEST-QC-W-DEL", false, 2, true);
        Long reopened = AnalysisTestData.insertCase(jdbcTemplate, "TEST-QC-W-OPEN", false, 2, false);
        Long stale = AnalysisTestData.insertCase(jdbcTemplate, "TEST-QC-W-STALE", true, 2, false);
        long missing = 999_999_999L;
        when(qdrantClient.search(any(), anyInt())).thenReturn(List.of(
                point(valid, 0.95, 1), point(deletedOpen, 0.9, 1), point(missing, 0.85, 1),
                point(reopened, 0.8, 1), point(stale, 0.75, 1)));

        assertThat(worker.pollOnce()).isTrue();

        assertThat(analysis()).containsEntry("analysis_status", "CANDIDATES_READY").containsEntry("error_message", null);
        List<Map<String, Object>> saved = candidates();
        // MySQL에 없는 id(3위)만 빠지고 순위는 Qdrant 순서 그대로
        assertThat(saved).extracting(c -> c.get("qdrant_rank")).containsExactly(1, 2, 4, 5);
        assertThat(saved).extracting(c -> c.get("invalid_reason"))
                .containsExactly(null, "DELETED", "NOT_RESOLVED", "STALE_VERSION");
        assertThat(saved).extracting(c -> c.get("is_null")).containsOnlyNulls();
    }

    @Test
    void 근거로_쓸_후보가_없으면_NO_CANDIDATES() {
        Long stale = AnalysisTestData.insertCase(jdbcTemplate, "TEST-QC-W-STALE", true, 2, false);
        when(qdrantClient.search(any(), anyInt())).thenReturn(List.of(point(stale, 0.9, 1)));

        worker.pollOnce();

        assertThat(analysis()).containsEntry("analysis_status", "NO_CANDIDATES").containsEntry("error_message", null);
        assertThat(candidates()).hasSize(1);
    }

    @Test
    void 실행_시점_사례로_검색하고_대상이_삭제됐어도_진행() {
        jdbcTemplate.update("UPDATE quality_case SET title = '요청 뒤 수정한 제목', deleted_at = NOW() WHERE id = ?", targetId);
        when(qdrantClient.search(any(), anyInt())).thenReturn(List.of());

        worker.pollOnce();

        verify(embeddingClient).embedQuery(contains("요청 뒤 수정한 제목"));
        assertThat(analysis()).containsEntry("analysis_status", "NO_CANDIDATES");
    }

    @Test
    void 임베딩_실패시_재시도_없이_FAILED와_오류_기록() {
        when(embeddingClient.embedQuery(anyString())).thenThrow(new IllegalStateException("모델 로딩 실패"));

        worker.pollOnce();

        assertThat(analysis()).containsEntry("analysis_status", "FAILED");
        assertThat((String) analysis().get("error_message")).contains("모델 로딩 실패");
        assertThat(candidates()).isEmpty();
    }

    @Test
    void Qdrant_실패시_FAILED() {
        when(qdrantClient.search(any(), anyInt())).thenThrow(new IllegalStateException("Qdrant 연결 실패"));

        worker.pollOnce();

        assertThat(analysis()).containsEntry("analysis_status", "FAILED");
        assertThat((String) analysis().get("error_message")).contains("Qdrant 연결 실패");
    }

    @Test
    void 기동시_검색중이던_분석은_PENDING으로_되돌린다() {
        jdbcTemplate.update("UPDATE analysis SET analysis_status = 'SEARCHING' WHERE id = ?", analysisId);

        worker.recoverInterrupted();

        assertThat(analysis()).containsEntry("analysis_status", "PENDING");
    }
}
