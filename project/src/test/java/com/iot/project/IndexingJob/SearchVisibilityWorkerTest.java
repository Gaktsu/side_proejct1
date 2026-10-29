package com.iot.project.IndexingJob;

import com.iot.project.QualityCase.ResolveTestData;
import com.iot.project.qdrant.QdrantClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.mockito.InOrder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

// IndexingWorkerTest와 같은 방식: 처리기는 꺼져 있고(test config) pollOnce를 직접 호출, 재시도 없음
@SpringBootTest
class SearchVisibilityWorkerTest {

    @Autowired
    SearchVisibilityWorker worker;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    QdrantClient qdrantClient;

    @AfterEach
    void cleanUp() {
        ResolveTestData.deleteAll(jdbcTemplate);
    }

    private Long insertJob(Long caseId) {
        jdbcTemplate.update("""
                INSERT INTO search_visibility_job (quality_case_id, status, created_at, updated_at)
                VALUES (?, 'PENDING', NOW(), NOW())""", caseId);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM search_visibility_job WHERE quality_case_id = ?", Long.class, caseId);
    }

    private Map<String, Object> job(Long jobId) {
        return jdbcTemplate.queryForMap("SELECT status, last_error FROM search_visibility_job WHERE id = ?", jobId);
    }

    @Test
    void RESOLVED이고_삭제_안됐으면_현재_버전일때만_true() {
        Long caseId = ResolveTestData.insertResolvedCase(jdbcTemplate, 2);
        Long jobId = insertJob(caseId);

        assertThat(worker.pollOnce()).isTrue();

        verify(qdrantClient).setSearchable(caseId, true, 2);
        assertThat(job(jobId)).containsEntry("status", "COMPLETED");
    }

    @Test
    void 삭제된_RESOLVED는_false() {
        Long caseId = ResolveTestData.insertResolvedCase(jdbcTemplate, 1);
        jdbcTemplate.update("UPDATE quality_case SET deleted_at = NOW() WHERE id = ?", caseId);
        insertJob(caseId);

        worker.pollOnce();

        verify(qdrantClient).setSearchable(caseId, false, 1);
    }

    @Test
    void OPEN은_false() {
        Long caseId = ResolveTestData.insertOpenCase(jdbcTemplate);
        insertJob(caseId);

        worker.pollOnce();

        verify(qdrantClient).setSearchable(caseId, false, 0);
    }

    @Test
    void job이_여러개여도_각자_실행시점의_MySQL_상태로_반영() {
        Long caseId = ResolveTestData.insertResolvedCase(jdbcTemplate, 1);
        insertJob(caseId);
        insertJob(caseId);

        worker.pollOnce();
        // 두 번째 job 처리 전에 삭제가 커밋된 상황
        jdbcTemplate.update("UPDATE quality_case SET deleted_at = NOW() WHERE id = ?", caseId);
        worker.pollOnce();

        InOrder order = inOrder(qdrantClient);
        order.verify(qdrantClient).setSearchable(caseId, true, 1);
        order.verify(qdrantClient).setSearchable(caseId, false, 1);
    }

    @Test
    void Qdrant_실패시_FAILED() {
        Long caseId = ResolveTestData.insertResolvedCase(jdbcTemplate, 1);
        Long jobId = insertJob(caseId);
        doThrow(new IllegalStateException("Qdrant 연결 실패")).when(qdrantClient).setSearchable(anyLong(), anyBoolean(), anyInt());

        worker.pollOnce();

        assertThat(job(jobId)).containsEntry("status", "FAILED");
        assertThat((String) job(jobId).get("last_error")).contains("Qdrant 연결 실패");
    }

    @Test
    void 기동시_처리중이던_job은_PENDING으로_되돌린다() {
        Long caseId = ResolveTestData.insertResolvedCase(jdbcTemplate, 1);
        Long jobId = insertJob(caseId);
        jdbcTemplate.update("UPDATE search_visibility_job SET status = 'PROCESSING' WHERE id = ?", jobId);

        worker.recoverInterrupted();

        assertThat(job(jobId)).containsEntry("status", "PENDING");
    }
}
