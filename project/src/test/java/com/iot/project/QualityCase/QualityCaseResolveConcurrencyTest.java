package com.iot.project.QualityCase;

import com.iot.project.common.ConcurrentRequests;
import com.iot.project.embedding.EmbeddingClient;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// 각 요청이 실제로 커밋되어야 경쟁이 일어나므로 @Transactional을 붙이지 않고 직접 정리한다
@SpringBootTest
@AutoConfigureMockMvc
class QualityCaseResolveConcurrencyTest {

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

    @Test
    void 같은_사례_동시_해결시_하나만_200_나머지는_409_job도_하나() throws Exception {
        List<Integer> statuses = ConcurrentRequests.run(10, () -> mockMvc.perform(
                        post("/api/quality-cases/{id}/resolve", caseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"rootCause": "용접 전류 저하", "correctiveAction": "전류 설정 복구"}
                                        """))
                .andReturn().getResponse().getStatus());

        assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(9);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM indexing_job WHERE quality_case_id = ?",
                Integer.class, caseId)).isEqualTo(1);
    }
}
