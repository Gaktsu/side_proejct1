package com.iot.project.IndexingJob;

import com.iot.project.QualityCase.ResolveTestData;
import com.iot.project.embedding.EmbeddingClient;
import com.iot.project.qdrant.QdrantClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 처리기는 자기 트랜잭션으로 커밋하므로 @Transactional 없이 직접 넣고 지운다
// 스케줄은 꺼져 있고(test config) pollOnce를 직접 호출한다. 재시도 없음(retry-delays 비움) → 첫 실패가 최종 실패
@SpringBootTest
class IndexingWorkerTest {

    @Autowired
    IndexingWorker worker;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    EmbeddingClient embeddingClient;

    @MockitoBean
    QdrantClient qdrantClient;

    private final float[] vector = new float[1024];

    @AfterEach
    void cleanUp() {
        ResolveTestData.deleteAll(jdbcTemplate);
    }

    private Map<String, Object> job(Long jobId) {
        return jdbcTemplate.queryForMap("SELECT embedding_status, qdrant_status, last_error FROM indexing_job WHERE id = ?", jobId);
    }

    @Test
    void 성공시_임베딩후_버전과_함께_upsert_둘다_COMPLETED() {
        Long caseId = ResolveTestData.insertResolvedCase(jdbcTemplate, 1);
        Long jobId = ResolveTestData.insertJob(jdbcTemplate, caseId, 1, "PENDING", "WAITING");
        when(embeddingClient.embedDocument(anyString())).thenReturn(vector);

        assertThat(worker.pollOnce()).isTrue();

        assertThat(job(jobId)).containsEntry("embedding_status", "COMPLETED").containsEntry("qdrant_status", "COMPLETED");
        verify(qdrantClient).upsert(caseId, vector, 1);
    }

    @Test
    void 임베딩_실패시_FAILED_Qdrant는_WAITING() {
        Long caseId = ResolveTestData.insertResolvedCase(jdbcTemplate, 1);
        Long jobId = ResolveTestData.insertJob(jdbcTemplate, caseId, 1, "PENDING", "WAITING");
        when(embeddingClient.embedDocument(anyString())).thenThrow(new IllegalStateException("모델 로딩 실패"));

        worker.pollOnce();

        assertThat(job(jobId)).containsEntry("embedding_status", "FAILED").containsEntry("qdrant_status", "WAITING");
        assertThat((String) job(jobId).get("last_error")).contains("모델 로딩 실패");
        verify(qdrantClient, never()).upsert(anyLong(), any(), anyInt());
    }

    @Test
    void Qdrant_실패시_임베딩은_COMPLETED_Qdrant는_FAILED() {
        Long caseId = ResolveTestData.insertResolvedCase(jdbcTemplate, 1);
        Long jobId = ResolveTestData.insertJob(jdbcTemplate, caseId, 1, "PENDING", "WAITING");
        when(embeddingClient.embedDocument(anyString())).thenReturn(vector);
        doThrow(new IllegalStateException("Qdrant 연결 실패")).when(qdrantClient).upsert(anyLong(), any(), anyInt());

        worker.pollOnce();

        assertThat(job(jobId)).containsEntry("embedding_status", "COMPLETED").containsEntry("qdrant_status", "FAILED");
    }

    @Test
    void 처리_전에_사례가_수정된_옛_job은_SUPERSEDED_임베딩_안함() {
        Long caseId = ResolveTestData.insertResolvedCase(jdbcTemplate, 2);
        Long oldJobId = ResolveTestData.insertJob(jdbcTemplate, caseId, 1, "PENDING", "WAITING");

        worker.pollOnce();

        assertThat(job(oldJobId)).containsEntry("embedding_status", "SUPERSEDED").containsEntry("qdrant_status", "WAITING");
        verify(embeddingClient, never()).embedDocument(anyString());
        verify(qdrantClient, never()).upsert(anyLong(), any(), anyInt());
    }

    @Test
    void 기동시_처리중이던_단계는_PENDING으로_되돌린다() {
        Long caseId = ResolveTestData.insertResolvedCase(jdbcTemplate, 1);
        Long jobId = ResolveTestData.insertJob(jdbcTemplate, caseId, 1, "PROCESSING", "WAITING");

        worker.recoverInterrupted();

        assertThat(job(jobId)).containsEntry("embedding_status", "PENDING");
    }
}
