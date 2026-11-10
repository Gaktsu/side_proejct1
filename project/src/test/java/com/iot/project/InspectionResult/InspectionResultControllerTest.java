package com.iot.project.InspectionResult;

import com.iot.project.QualityCase.ResolveTestData;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InspectionResultControllerTest {

    private static final String WIDTH_FAIL = """
            {"inspectionItem": "용접부 폭", "inspectionMethod": "버니어캘리퍼스", "numericValue": 3.8, "unit": "mm",
             "specMin": 4.0, "specMax": 5.0, "resultStatus": "FAIL", "measuredAt": "2026-11-09T10:00:00"}
            """;
    private static final String SURFACE_FAIL = """
            {"inspectionItem": "표면 상태", "textValue": "모서리 도막 박리", "specText": "박리 및 기포 없음", "resultStatus": "FAIL"}
            """;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManager entityManager;

    private ResultActions postResult(Long caseId, String body) throws Exception {
        return mockMvc.perform(post("/api/quality-cases/" + caseId + "/inspection-results")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions putResult(Long id, String body) throws Exception {
        return mockMvc.perform(put("/api/inspection-results/" + id)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Long createResult(Long caseId, String body) throws Exception {
        String json = postResult(caseId, body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private void softDeleteCase(Long caseId) throws Exception {
        mockMvc.perform(delete("/api/quality-cases/" + caseId)).andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();
    }

    private Integer countRows(String table, Long caseId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE quality_case_id = ?",
                Integer.class, caseId);
    }

    @Test
    void 등록_201_입력값과_사례id_반환() throws Exception {
        Long caseId = ResolveTestData.insertOpenCase(jdbcTemplate);

        postResult(caseId, WIDTH_FAIL)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.qualityCaseId").value(caseId))
                .andExpect(jsonPath("$.inspectionItem").value("용접부 폭"))
                .andExpect(jsonPath("$.inspectionMethod").value("버니어캘리퍼스"))
                .andExpect(jsonPath("$.numericValue").value(3.8))
                .andExpect(jsonPath("$.unit").value("mm"))
                .andExpect(jsonPath("$.specMin").value(4.0))
                .andExpect(jsonPath("$.specMax").value(5.0))
                .andExpect(jsonPath("$.resultStatus").value("FAIL"))
                .andExpect(jsonPath("$.measuredAt").value("2026-11-09T10:00:00"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void 등록_없는_사례_404() throws Exception {
        postResult(Long.MAX_VALUE, WIDTH_FAIL).andExpect(status().isNotFound());
    }

    @Test
    void 등록_논리_삭제된_사례_404() throws Exception {
        Long caseId = ResolveTestData.insertOpenCase(jdbcTemplate);
        softDeleteCase(caseId);

        postResult(caseId, WIDTH_FAIL).andExpect(status().isNotFound());
    }

    @Test
    void RESOLVED_사례도_등록_수정_삭제_가능하고_버전과_인덱싱은_그대로() throws Exception {
        Long caseId = ResolveTestData.insertResolvedCase(jdbcTemplate, 3);
        Object updatedAtBefore = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM quality_case WHERE id = ?", Object.class, caseId);

        Long resultId = createResult(caseId, WIDTH_FAIL);
        putResult(resultId, SURFACE_FAIL).andExpect(status().isOk());
        mockMvc.perform(delete("/api/inspection-results/" + resultId)).andExpect(status().isNoContent());
        entityManager.flush();

        assertThat(jdbcTemplate.queryForObject("SELECT source_version FROM quality_case WHERE id = ?",
                Integer.class, caseId)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM quality_case WHERE id = ?",
                String.class, caseId)).isEqualTo("RESOLVED");
        assertThat(jdbcTemplate.queryForObject("SELECT updated_at FROM quality_case WHERE id = ?",
                Object.class, caseId)).isEqualTo(updatedAtBefore);
        assertThat(countRows("indexing_job", caseId)).isZero();
        assertThat(countRows("search_visibility_job", caseId)).isZero();
        assertThat(countRows("quality_case_revision", caseId)).isZero();
    }

    @Test
    void 진행_중인_분석이_있어도_등록_가능() throws Exception {
        Long caseId = ResolveTestData.insertOpenCase(jdbcTemplate);
        jdbcTemplate.update("INSERT INTO analysis (target_quality_case_id, analysis_status, created_at) "
                + "VALUES (?, 'PENDING', NOW())", caseId);

        postResult(caseId, WIDTH_FAIL).andExpect(status().isCreated());
    }

    @Test
    void 측정값이_숫자_문자_모두_없거나_공백이면_400_저장_안_됨() throws Exception {
        Long caseId = ResolveTestData.insertOpenCase(jdbcTemplate);

        postResult(caseId, """
                {"inspectionItem": "용접부 폭", "resultStatus": "FAIL"}
                """).andExpect(status().isBadRequest());
        postResult(caseId, """
                {"inspectionItem": "용접부 폭", "textValue": "   ", "resultStatus": "FAIL"}
                """).andExpect(status().isBadRequest());

        assertThat(countRows("inspection_result", caseId)).isZero();
    }

    @Test
    void 숫자_문자_측정값을_둘_다_보내면_저장() throws Exception {
        Long caseId = ResolveTestData.insertOpenCase(jdbcTemplate);

        postResult(caseId, """
                {"inspectionItem": "용접부 폭", "numericValue": 3.8, "textValue": "모서리 박리", "resultStatus": "FAIL"}
                """).andExpect(status().isCreated())
                .andExpect(jsonPath("$.numericValue").value(3.8))
                .andExpect(jsonPath("$.textValue").value("모서리 박리"));
    }

    @Test
    void 규격_최소값이_최대값보다_크면_400_같거나_한쪽만_있으면_저장() throws Exception {
        Long caseId = ResolveTestData.insertOpenCase(jdbcTemplate);

        postResult(caseId, """
                {"inspectionItem": "폭", "numericValue": 3, "specMin": 20, "specMax": 10, "resultStatus": "FAIL"}
                """).andExpect(status().isBadRequest());
        postResult(caseId, """
                {"inspectionItem": "폭", "numericValue": 10, "specMin": 10, "specMax": 10, "resultStatus": "PASS"}
                """).andExpect(status().isCreated());
        postResult(caseId, """
                {"inspectionItem": "폭", "numericValue": 10, "specMin": 20, "resultStatus": "FAIL"}
                """).andExpect(status().isCreated());
    }

    @Test
    void 규격_밖_측정값도_거절하지_않고_resultStatus는_입력값_그대로_저장() throws Exception {
        Long caseId = ResolveTestData.insertOpenCase(jdbcTemplate);

        postResult(caseId, """
                {"inspectionItem": "폭", "numericValue": 25, "specMin": 10, "specMax": 20, "resultStatus": "FAIL"}
                """).andExpect(status().isCreated()).andExpect(jsonPath("$.resultStatus").value("FAIL"));
        postResult(caseId, """
                {"inspectionItem": "폭", "numericValue": 25, "specMin": 10, "specMax": 20, "resultStatus": "PASS"}
                """).andExpect(status().isCreated()).andExpect(jsonPath("$.resultStatus").value("PASS"));
    }

    @Test
    void 필수값_길이_자릿수_상태값이_잘못되면_400() throws Exception {
        Long caseId = ResolveTestData.insertOpenCase(jdbcTemplate);
        String[] bodies = {
                """
                {"inspectionItem": " ", "numericValue": 1, "resultStatus": "FAIL"}
                """,
                """
                {"inspectionItem": "%s", "numericValue": 1, "resultStatus": "FAIL"}
                """.formatted("가".repeat(101)),
                """
                {"inspectionItem": "폭", "textValue": "%s", "resultStatus": "FAIL"}
                """.formatted("가".repeat(256)),
                """
                {"inspectionItem": "폭", "numericValue": 1, "unit": "%s", "resultStatus": "FAIL"}
                """.formatted("a".repeat(31)),
                """
                {"inspectionItem": "폭", "numericValue": 1}
                """,
                """
                {"inspectionItem": "폭", "numericValue": 1, "resultStatus": "UNKNOWN"}
                """,
                """
                {"inspectionItem": "폭", "numericValue": 1234567890123, "resultStatus": "FAIL"}
                """,
                """
                {"inspectionItem": "폭", "numericValue": 1.1234567, "resultStatus": "FAIL"}
                """
        };

        for (String body : bodies) {
            postResult(caseId, body).andExpect(status().isBadRequest());
        }
        assertThat(countRows("inspection_result", caseId)).isZero();
    }

    @Test
    void 수정은_전체_교체라_보내지_않은_선택_필드는_비워지고_createdAt은_유지() throws Exception {
        Long caseId = ResolveTestData.insertOpenCase(jdbcTemplate);
        String created = postResult(caseId, WIDTH_FAIL).andReturn().getResponse().getContentAsString();
        Long resultId = ((Number) JsonPath.read(created, "$.id")).longValue();
        String createdAt = JsonPath.read(created, "$.createdAt");

        putResult(resultId, SURFACE_FAIL)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(resultId))
                .andExpect(jsonPath("$.qualityCaseId").value(caseId))
                .andExpect(jsonPath("$.inspectionItem").value("표면 상태"))
                .andExpect(jsonPath("$.textValue").value("모서리 도막 박리"))
                .andExpect(jsonPath("$.inspectionMethod").doesNotExist())
                .andExpect(jsonPath("$.numericValue").doesNotExist())
                .andExpect(jsonPath("$.unit").doesNotExist())
                .andExpect(jsonPath("$.specMin").doesNotExist())
                .andExpect(jsonPath("$.measuredAt").doesNotExist())
                .andExpect(jsonPath("$.createdAt").value(createdAt));
    }

    @Test
    void 수정도_측정값_규격_검증을_한다_400() throws Exception {
        Long caseId = ResolveTestData.insertOpenCase(jdbcTemplate);
        Long resultId = createResult(caseId, WIDTH_FAIL);

        putResult(resultId, """
                {"inspectionItem": "폭", "resultStatus": "FAIL"}
                """).andExpect(status().isBadRequest());
        putResult(resultId, """
                {"inspectionItem": "폭", "numericValue": 1, "specMin": 2, "specMax": 1, "resultStatus": "FAIL"}
                """).andExpect(status().isBadRequest());
    }

    @Test
    void 수정_삭제_없는_검사_결과_404() throws Exception {
        putResult(Long.MAX_VALUE, WIDTH_FAIL).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/inspection-results/" + Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void 소속_사례가_논리_삭제되면_수정_삭제_404이고_행은_유지() throws Exception {
        Long caseId = ResolveTestData.insertOpenCase(jdbcTemplate);
        Long resultId = createResult(caseId, WIDTH_FAIL);
        softDeleteCase(caseId);

        putResult(resultId, SURFACE_FAIL).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/inspection-results/" + resultId)).andExpect(status().isNotFound());
        assertThat(countRows("inspection_result", caseId)).isEqualTo(1);
    }

    @Test
    void 삭제하면_행이_없어지고_단건_응답에도_안_나오며_재수정은_404() throws Exception {
        Long caseId = ResolveTestData.insertOpenCase(jdbcTemplate);
        Long resultId = createResult(caseId, WIDTH_FAIL);

        mockMvc.perform(delete("/api/inspection-results/" + resultId)).andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();

        assertThat(countRows("inspection_result", caseId)).isZero();
        mockMvc.perform(get("/api/quality-cases/" + caseId))
                .andExpect(jsonPath("$.inspectionResults.length()").value(0));
        putResult(resultId, WIDTH_FAIL).andExpect(status().isNotFound());
    }

    @Test
    void 사례_단건_조회에_검사_결과_전부가_최신_등록순으로_나오고_기존_필드도_유지() throws Exception {
        Long caseId = ResolveTestData.insertOpenCase(jdbcTemplate);
        Long first = createResult(caseId, WIDTH_FAIL);
        Long second = createResult(caseId, SURFACE_FAIL);

        mockMvc.perform(get("/api/quality-cases/" + caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(caseId))
                .andExpect(jsonPath("$.caseCode").value("TEST-QC-RESOLVE"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.qualityCase").doesNotExist())
                .andExpect(jsonPath("$.inspectionResults.length()").value(2))
                .andExpect(jsonPath("$.inspectionResults[0].id").value(second))
                .andExpect(jsonPath("$.inspectionResults[0].qualityCaseId").value(caseId))
                .andExpect(jsonPath("$.inspectionResults[1].id").value(first))
                .andExpect(jsonPath("$.inspectionResults[1].inspectionItem").value("용접부 폭"));
    }

    @Test
    void 검사_결과가_없는_사례의_단건_조회는_빈_배열() throws Exception {
        Long caseId = ResolveTestData.insertOpenCase(jdbcTemplate);

        mockMvc.perform(get("/api/quality-cases/" + caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inspectionResults").isArray())
                .andExpect(jsonPath("$.inspectionResults.length()").value(0));
    }

    @Test
    void 사례_목록_응답에는_검사_결과가_없음() throws Exception {
        Long caseId = ResolveTestData.insertOpenCase(jdbcTemplate);
        createResult(caseId, WIDTH_FAIL);

        String list = mockMvc.perform(get("/api/quality-cases")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(list).contains("TEST-QC-RESOLVE").doesNotContain("inspectionResults");
    }

    @Test
    void 사례_논리_삭제_후_복구하면_검사_결과가_다시_조회됨() throws Exception {
        Long caseId = ResolveTestData.insertOpenCase(jdbcTemplate);
        createResult(caseId, WIDTH_FAIL);
        softDeleteCase(caseId);

        mockMvc.perform(get("/api/quality-cases/" + caseId)).andExpect(status().isNotFound());

        mockMvc.perform(post("/api/quality-cases/" + caseId + "/restore")).andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/quality-cases/" + caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inspectionResults.length()").value(1));
    }
}
