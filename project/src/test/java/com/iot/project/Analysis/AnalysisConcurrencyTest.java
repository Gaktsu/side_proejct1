package com.iot.project.Analysis;

import com.iot.project.QualityCase.ResolveTestData;
import com.iot.project.common.ConcurrentRequests;
import com.iot.project.embedding.EmbeddingClient;
import com.iot.project.qdrant.QdrantClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// 각 요청이 실제로 커밋되어야 경쟁이 일어나므로 @Transactional을 붙이지 않고 직접 정리한다
@SpringBootTest
@AutoConfigureMockMvc
class AnalysisConcurrencyTest {

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
        AnalysisTestData.insertMasters(jdbcTemplate);
        caseId = AnalysisTestData.insertCase(jdbcTemplate, "TEST-QC-C-OPEN", false, 0, false);
    }

    @AfterEach
    void cleanUp() {
        ResolveTestData.deleteAll(jdbcTemplate);
    }

    @Test
    void 같은_사례_동시_분석요청시_하나만_202_나머지는_409_분석도_하나() throws Exception {
        List<Integer> statuses = ConcurrentRequests.run(10, () -> mockMvc.perform(
                        post("/api/quality-cases/{id}/analyses", caseId))
                .andReturn().getResponse().getStatus());

        assertThat(statuses).filteredOn(s -> s == 202).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(9);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM analysis WHERE target_quality_case_id = ?",
                Integer.class, caseId)).isEqualTo(1);
    }
}
