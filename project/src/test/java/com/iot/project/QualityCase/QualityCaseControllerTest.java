package com.iot.project.QualityCase;

import com.iot.project.Equipment.Equipment;
import com.iot.project.Equipment.EquipmentRepository;
import com.iot.project.ManufacturingProcess.ManufacturingProcess;
import com.iot.project.ManufacturingProcess.ManufacturingProcessRepository;
import com.iot.project.Product.Product;
import com.iot.project.Product.ProductRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
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
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QualityCaseControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    QualityCaseRepository qualityCaseRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    ManufacturingProcessRepository manufacturingProcessRepository;

    @Autowired
    EquipmentRepository equipmentRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManager entityManager;

    private Long productId;
    private Long processId;
    private Long equipmentId;
    private Long otherProcessEquipmentId;

    @BeforeEach
    void setUp() {
        productId = productRepository.save(new Product("TEST-P-001", "브레이크 캘리퍼", null, null)).getId();
        ManufacturingProcess welding = manufacturingProcessRepository.save(new ManufacturingProcess("TEST-PR-001", "용접", null));
        ManufacturingProcess painting = manufacturingProcessRepository.save(new ManufacturingProcess("TEST-PR-002", "도장", null));
        processId = welding.getId();
        equipmentId = equipmentRepository.save(new Equipment(welding, "TEST-EQ-001", "용접기 1호", null)).getId();
        otherProcessEquipmentId = equipmentRepository.save(new Equipment(painting, "TEST-EQ-002", "도장기 1호", null)).getId();
    }

    private ResultActions postCase(Long productId, Long processId, Long equipmentId, int production, int defect) throws Exception {
        return mockMvc.perform(post("/api/quality-cases").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "productId": %s, "processId": %s, "equipmentId": %s,
                          "lotNo": "LOT-20261005-01", "title": "용접부 미세 균열",
                          "problemDescription": "출하 검사에서 용접부 미세 균열 발견",
                          "defectType": "크랙", "productionQuantity": %d, "defectQuantity": %d,
                          "occurredAt": "2026-10-05T09:30:00"
                        }
                        """.formatted(productId, processId, equipmentId, production, defect)));
    }

    private Long createCase() throws Exception {
        String json = postCase(productId, processId, equipmentId, 100, 3)
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    @Test
    void 품질사례_등록시_OPEN_상태와_사례번호_생성() throws Exception {
        postCase(productId, processId, equipmentId, 100, 3)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.caseCode").value(org.hamcrest.Matchers.matchesPattern("QC-\\d{8}-[0-9A-F]{8}")))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.processId").value(processId))
                .andExpect(jsonPath("$.equipmentId").value(equipmentId));
    }

    @Test
    void 설비_없이_등록_가능() throws Exception {
        postCase(productId, processId, null, 100, 3)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.equipmentId").doesNotExist());
    }

    @Test
    void 불량수량이_생산수량보다_크면_400() throws Exception {
        postCase(productId, processId, equipmentId, 10, 11)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("불량 수량은 생산 수량보다 클 수 없습니다. productionQuantity=10, defectQuantity=11"));
    }

    @Test
    void 수량이_음수면_400() throws Exception {
        postCase(productId, processId, equipmentId, -1, 0)
                .andExpect(status().isBadRequest());
    }

    @Test
    void 없는_제품이면_404() throws Exception {
        postCase(Long.MAX_VALUE, processId, equipmentId, 100, 3)
                .andExpect(status().isNotFound());
    }

    @Test
    void 없는_공정이면_404() throws Exception {
        postCase(productId, Long.MAX_VALUE, equipmentId, 100, 3)
                .andExpect(status().isNotFound());
    }

    @Test
    void 없는_설비면_404() throws Exception {
        postCase(productId, processId, Long.MAX_VALUE, 100, 3)
                .andExpect(status().isNotFound());
    }

    @Test
    void 설비가_다른_공정_소속이면_400() throws Exception {
        postCase(productId, processId, otherProcessEquipmentId, 100, 3)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("선택한 설비가 선택한 공정에 속하지 않습니다. equipmentId="
                        + otherProcessEquipmentId + ", processId=" + processId));
    }

    // resolve API는 임베딩 토큰 검사가 필요하므로 DB에서 직접 RESOLVED(버전 1)로 만든다
    private Long createResolvedCase() throws Exception {
        Long id = createCase();
        jdbcTemplate.update("""
                UPDATE quality_case SET status = 'RESOLVED', root_cause = '용접 전류 저하',
                    corrective_action = '전류 설정 복구', result_summary = '재발 없음', source_version = 1
                WHERE id = ?""", id);
        // 실제 요청처럼 별도 영속성 컨텍스트에서 조회하도록 비움
        entityManager.clear();
        return id;
    }

    // 테스트 트랜잭션 안의 변경을 DB에 내보내고(JdbcTemplate 조회용) 다음 요청이 새로 읽게 한다
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private int visibilityJobCount(Long id) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM search_visibility_job WHERE quality_case_id = ? AND status = 'PENDING'", Integer.class, id);
    }

    @Test
    void 품질사례_삭제시_행은_남고_deleted_at과_노출job_기록() throws Exception {
        Long id = createCase();
        mockMvc.perform(delete("/api/quality-cases/{id}", id))
                .andExpect(status().isNoContent());
        flushAndClear();

        assertThat(jdbcTemplate.queryForObject("SELECT deleted_at IS NOT NULL FROM quality_case WHERE id = ?", Boolean.class, id)).isTrue();
        assertThat(visibilityJobCount(id)).isEqualTo(1);
        mockMvc.perform(get("/api/quality-cases/{id}", id)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/quality-cases")).andExpect(jsonPath("$[?(@.id == %d)]".formatted(id)).isEmpty());
    }

    @Test
    void RESOLVED_품질사례도_삭제되고_상태와_버전은_유지() throws Exception {
        Long id = createResolvedCase();
        mockMvc.perform(delete("/api/quality-cases/{id}", id))
                .andExpect(status().isNoContent());
        flushAndClear();

        assertThat(jdbcTemplate.queryForMap("SELECT status, source_version FROM quality_case WHERE id = ?", id))
                .containsEntry("status", "RESOLVED").containsEntry("source_version", 1);
    }

    @Test
    void 검사결과가_있어도_논리_삭제는_204() throws Exception {
        Long id = createCase();
        jdbcTemplate.update("""
                INSERT INTO inspection_result (quality_case_id, inspection_item, text_value, result_status, created_at)
                VALUES (?, '표면 상태', '균열', 'FAIL', NOW())
                """, id);
        entityManager.clear();

        mockMvc.perform(delete("/api/quality-cases/{id}", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void 없는_품질사례_삭제시_404() throws Exception {
        mockMvc.perform(delete("/api/quality-cases/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void 삭제된_사례는_다시_삭제_수정_해결_취소_이력_모두_404() throws Exception {
        Long id = createResolvedCase();
        mockMvc.perform(delete("/api/quality-cases/{id}", id));
        flushAndClear();

        mockMvc.perform(delete("/api/quality-cases/{id}", id)).andExpect(status().isNotFound());
        updateCase(id, equipmentId, 100, 3).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/quality-cases/{id}/resolve", id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rootCause": "원인", "correctiveAction": "조치"}"""))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/quality-cases/{id}/reopen", id)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/quality-cases/{id}/revisions", id)).andExpect(status().isNotFound());
    }

    @Test
    void 삭제된_사례_복구시_삭제전_상태_그대로_200_노출job_추가() throws Exception {
        Long id = createResolvedCase();
        mockMvc.perform(delete("/api/quality-cases/{id}", id));
        flushAndClear();

        mockMvc.perform(post("/api/quality-cases/{id}/restore", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.sourceVersion").value(1))
                .andExpect(jsonPath("$.rootCause").value("용접 전류 저하"));
        flushAndClear();

        assertThat(jdbcTemplate.queryForObject("SELECT deleted_at IS NULL FROM quality_case WHERE id = ?", Boolean.class, id)).isTrue();
        assertThat(visibilityJobCount(id)).isEqualTo(2);
        mockMvc.perform(get("/api/quality-cases/{id}", id)).andExpect(status().isOk());
    }

    @Test
    void 삭제되지_않은_사례_복구시_409() throws Exception {
        Long id = createCase();
        mockMvc.perform(post("/api/quality-cases/{id}/restore", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("삭제된 품질사례만 복구할 수 있습니다. id=" + id));
    }

    @Test
    void 없는_사례_복구시_404() throws Exception {
        mockMvc.perform(post("/api/quality-cases/{id}/restore", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void 해결_취소시_OPEN_원인조치결과_비움_이력_버전증가_노출job() throws Exception {
        Long id = createResolvedCase();

        mockMvc.perform(post("/api/quality-cases/{id}/reopen", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.rootCause").doesNotExist())
                .andExpect(jsonPath("$.correctiveAction").doesNotExist())
                .andExpect(jsonPath("$.resultSummary").doesNotExist())
                .andExpect(jsonPath("$.sourceVersion").value(2));

        assertThat(jdbcTemplate.queryForMap(
                "SELECT version, root_cause, corrective_action, result_summary FROM quality_case_revision WHERE quality_case_id = ?", id))
                .containsEntry("version", 1).containsEntry("root_cause", "용접 전류 저하")
                .containsEntry("corrective_action", "전류 설정 복구").containsEntry("result_summary", "재발 없음");
        assertThat(visibilityJobCount(id)).isEqualTo(1);
    }

    @Test
    void OPEN_사례_해결_취소시_409() throws Exception {
        Long id = createCase();
        mockMvc.perform(post("/api/quality-cases/{id}/reopen", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("해결 완료(RESOLVED)된 품질사례만 해결 취소할 수 있습니다. id=" + id));
    }

    @Test
    void 없는_사례_해결_취소시_404() throws Exception {
        mockMvc.perform(post("/api/quality-cases/{id}/reopen", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void 품질사례가_있는_제품은_삭제시_409() throws Exception {
        createCase();
        entityManager.clear();

        mockMvc.perform(delete("/api/products/{id}", productId))
                .andExpect(status().isConflict());
    }

    private ResultActions updateCase(Long id, Long equipmentId, int production, int defect) throws Exception {
        return mockMvc.perform(put("/api/quality-cases/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "productId": %s, "processId": %s, "equipmentId": %s,
                          "lotNo": "LOT-20261005-02", "title": "용접부 균열 (재확인)",
                          "problemDescription": "재검사 결과 균열 길이 2mm",
                          "defectType": "크랙", "productionQuantity": %d, "defectQuantity": %d,
                          "occurredAt": "2026-10-05T10:00:00"
                        }
                        """.formatted(productId, processId, equipmentId, production, defect)));
    }

    @Test
    void 품질사례_목록_조회() throws Exception {
        Long id = createCase();
        mockMvc.perform(get("/api/quality-cases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(id.intValue())));
    }

    @Test
    void 품질사례_단건_조회() throws Exception {
        Long id = createCase();
        mockMvc.perform(get("/api/quality-cases/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("용접부 미세 균열"))
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void 없는_품질사례_조회시_404() throws Exception {
        mockMvc.perform(get("/api/quality-cases/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void 품질사례_수정시_보낸_값으로_교체되고_사례번호와_상태는_유지() throws Exception {
        String json = postCase(productId, processId, equipmentId, 100, 3)
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(json, "$.id")).longValue();
        String caseCode = JsonPath.read(json, "$.caseCode");

        updateCase(id, null, 200, 5)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseCode").value(caseCode))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.equipmentId").doesNotExist())
                .andExpect(jsonPath("$.title").value("용접부 균열 (재확인)"))
                .andExpect(jsonPath("$.productionQuantity").value(200))
                .andExpect(jsonPath("$.defectQuantity").value(5));
    }

    @Test
    void 없는_품질사례_수정시_404() throws Exception {
        updateCase(Long.MAX_VALUE, equipmentId, 100, 3)
                .andExpect(status().isNotFound());
    }

    @Test
    void 수정시_불량수량이_생산수량보다_크면_400() throws Exception {
        Long id = createCase();
        updateCase(id, equipmentId, 10, 11)
                .andExpect(status().isBadRequest());
    }

    @Test
    void 수정시_설비가_다른_공정_소속이면_400() throws Exception {
        Long id = createCase();
        updateCase(id, otherProcessEquipmentId, 100, 3)
                .andExpect(status().isBadRequest());
    }

    @Test
    void 수정시_없는_설비면_404() throws Exception {
        Long id = createCase();
        updateCase(id, Long.MAX_VALUE, 100, 3)
                .andExpect(status().isNotFound());
    }
}
