package com.iot.project.Analysis;

import com.iot.project.embedding.EmbeddingClient;
import com.iot.project.embedding.EmbeddingInputTooLongException;
import com.iot.project.qdrant.QdrantClient;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 요청·조회 API만 (검색은 AnalysisWorkerTest). 처리기는 test config에서 꺼져 있어 PENDING으로 남는다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnalysisControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManager entityManager;

    @MockitoBean
    EmbeddingClient embeddingClient;

    @MockitoBean
    QdrantClient qdrantClient;

    private Long openId;

    @BeforeEach
    void setUp() {
        AnalysisTestData.insertMasters(jdbcTemplate);
        openId = AnalysisTestData.insertCase(jdbcTemplate, "TEST-QC-A-OPEN", false, 0, false);
    }

    private int analysisCount(Long caseId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM analysis WHERE target_quality_case_id = ?", Integer.class, caseId);
    }

    private void insertCandidate(Long analysisId, Long caseId, int rank) {
        jdbcTemplate.update("""
                INSERT INTO analysis_candidate (analysis_id, candidate_quality_case_id, qdrant_rank, vector_score, created_at)
                VALUES (?, ?, ?, 0.9, NOW())""", analysisId, caseId, rank);
    }

    @Test
    void 분석_요청시_202_PENDING_분석_생성() throws Exception {
        mockMvc.perform(post("/api/quality-cases/{id}/analyses", openId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.analysisId").isNumber())
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertThat(analysisCount(openId)).isEqualTo(1);
    }

    @Test
    void 없는_사례_404() throws Exception {
        mockMvc.perform(post("/api/quality-cases/{id}/analyses", 999_999_999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void 삭제된_사례_404() throws Exception {
        Long deletedId = AnalysisTestData.insertCase(jdbcTemplate, "TEST-QC-A-DEL", false, 0, true);

        mockMvc.perform(post("/api/quality-cases/{id}/analyses", deletedId))
                .andExpect(status().isNotFound());
    }

    @Test
    void RESOLVED_사례_409() throws Exception {
        Long resolvedId = AnalysisTestData.insertCase(jdbcTemplate, "TEST-QC-A-RES", true, 1, false);

        mockMvc.perform(post("/api/quality-cases/{id}/analyses", resolvedId))
                .andExpect(status().isConflict());
        assertThat(analysisCount(resolvedId)).isZero();
    }

    @Test
    void 진행중_분석이_있으면_409와_기존_analysisId() throws Exception {
        Long existingId = AnalysisTestData.insertAnalysis(jdbcTemplate, openId, "SEARCHING");

        mockMvc.perform(post("/api/quality-cases/{id}/analyses", openId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.analysisId").value(existingId))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("이미 등록된 분석 요청입니다")));
        assertThat(analysisCount(openId)).isEqualTo(1);
    }

    @Test
    void 끝난_분석이_있으면_후보까지_지우고_새로_만든다() throws Exception {
        Long oldId = AnalysisTestData.insertAnalysis(jdbcTemplate, openId, "CANDIDATES_READY");
        Long candidateCaseId = AnalysisTestData.insertCase(jdbcTemplate, "TEST-QC-A-CAND", true, 1, false);
        insertCandidate(oldId, candidateCaseId, 1);

        mockMvc.perform(post("/api/quality-cases/{id}/analyses", openId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.analysisId").value(not(oldId.intValue())))
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertThat(analysisCount(openId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM analysis_candidate WHERE analysis_id = ?",
                Integer.class, oldId)).isZero();
    }

    @Test
    void 토큰_상한_초과시_400_기존_분석은_그대로() throws Exception {
        Long oldId = AnalysisTestData.insertAnalysis(jdbcTemplate, openId, "FAILED");
        doThrow(new EmbeddingInputTooLongException(1100, 1024)).when(embeddingClient).checkQueryLength(anyString());

        mockMvc.perform(post("/api/quality-cases/{id}/analyses", openId))
                .andExpect(status().isBadRequest());

        assertThat(jdbcTemplate.queryForObject("SELECT id FROM analysis WHERE target_quality_case_id = ?",
                Long.class, openId)).isEqualTo(oldId);
    }

    @Test
    void 조회시_상태와_후보를_Qdrant_순위대로() throws Exception {
        Long analysisId = AnalysisTestData.insertAnalysis(jdbcTemplate, openId, "CANDIDATES_READY");
        Long second = AnalysisTestData.insertCase(jdbcTemplate, "TEST-QC-A-C2", true, 1, false);
        Long first = AnalysisTestData.insertCase(jdbcTemplate, "TEST-QC-A-C1", true, 1, false);
        insertCandidate(analysisId, second, 2);
        insertCandidate(analysisId, first, 1);

        mockMvc.perform(get("/api/analyses/{id}", analysisId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANDIDATES_READY"))
                .andExpect(jsonPath("$.qualityCaseId").value(openId))
                .andExpect(jsonPath("$.candidates", hasSize(2)))
                .andExpect(jsonPath("$.candidates[0].qualityCaseId").value(first))
                .andExpect(jsonPath("$.candidates[0].caseCode").value("TEST-QC-A-C1"))
                .andExpect(jsonPath("$.candidates[0].invalidReason").isEmpty())
                .andExpect(jsonPath("$.candidates[1].qualityCaseId").value(second));
    }

    @Test
    void 대상_사례가_삭제되면_조회_404_복구하면_200() throws Exception {
        Long analysisId = AnalysisTestData.insertAnalysis(jdbcTemplate, openId, "NO_CANDIDATES");
        jdbcTemplate.update("UPDATE quality_case SET deleted_at = NOW() WHERE id = ?", openId);

        mockMvc.perform(get("/api/analyses/{id}", analysisId))
                .andExpect(status().isNotFound());

        jdbcTemplate.update("UPDATE quality_case SET deleted_at = NULL WHERE id = ?", openId);
        // 실제 요청은 매번 새 영속성 컨텍스트. 테스트 트랜잭션 하나로 묶여 있어 앞 요청이 읽은 사례를 비운다
        entityManager.clear();
        mockMvc.perform(get("/api/analyses/{id}", analysisId))
                .andExpect(status().isOk());
    }

    @Test
    void 없는_분석_조회_404() throws Exception {
        mockMvc.perform(get("/api/analyses/{id}", 999_999_999L))
                .andExpect(status().isNotFound());
    }
}
