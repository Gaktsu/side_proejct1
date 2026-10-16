package com.iot.project.QualityCase;

import com.iot.project.embedding.EmbeddingClient;
import com.iot.project.embedding.EmbeddingInputTooLongException;
import com.iot.project.qdrant.QdrantClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 롤백은 실제 커밋 여부로만 확인할 수 있어 @Transactional 없이 실행하고 직접 정리한다
@SpringBootTest
@AutoConfigureMockMvc
class QualityCaseResolveRollbackTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    EmbeddingClient embeddingClient;

    @MockitoBean
    QdrantClient qdrantClient;

    private Long caseId;

    @BeforeEach
    void setUp() {
        caseId = ResolveTestData.insertOpenCase(jdbcTemplate);
    }

    @AfterEach
    void cleanUp() {
        ResolveTestData.deleteAll(jdbcTemplate);
    }

    private ResultActions resolve() throws Exception {
        return mockMvc.perform(post("/api/quality-cases/{id}/resolve", caseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"rootCause": "용접 전류 저하", "correctiveAction": "전류 설정 복구", "resultSummary": "재발 없음"}
                        """));
    }

    private void assertStillOpen() {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, root_cause, corrective_action, result_summary FROM quality_case WHERE id = ?", caseId);
        assertThat(row.get("status")).isEqualTo("OPEN");
        assertThat(row.get("root_cause")).isNull();
        assertThat(row.get("corrective_action")).isNull();
        assertThat(row.get("result_summary")).isNull();
    }

    @Test
    void 임베딩_실패시_503_OPEN_유지() throws Exception {
        when(embeddingClient.embedDocument(anyString())).thenThrow(new IllegalStateException("임베딩 모델 로딩 실패"));

        resolve().andExpect(status().isServiceUnavailable());

        assertStillOpen();
    }

    @Test
    void Qdrant_실패시_503_OPEN_유지() throws Exception {
        when(embeddingClient.embedDocument(anyString())).thenReturn(new float[1024]);
        doThrow(new ResourceAccessException("Connection refused")).when(qdrantClient).upsert(anyLong(), any());

        resolve().andExpect(status().isServiceUnavailable());

        assertStillOpen();
    }

    @Test
    void 토큰_상한_초과시_400_OPEN_유지_적재_안함() throws Exception {
        when(embeddingClient.embedDocument(anyString())).thenThrow(new EmbeddingInputTooLongException(1133, 1024));

        resolve().andExpect(status().isBadRequest());

        assertStillOpen();
        verify(qdrantClient, never()).upsert(anyLong(), any());
    }
}
