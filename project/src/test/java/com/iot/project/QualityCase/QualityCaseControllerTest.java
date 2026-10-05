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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TQualityCaseControllerTest {

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

    @Test
    void 품질사례_삭제() throws Exception {
        Long id = createCase();
        mockMvc.perform(delete("/api/quality-cases/{id}", id))
                .andExpect(status().isNoContent());
        assertThat(qualityCaseRepository.existsById(id)).isFalse();
    }

    @Test
    void 없는_품질사례_삭제시_404() throws Exception {
        mockMvc.perform(delete("/api/quality-cases/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void RESOLVED_품질사례_삭제시_409() throws Exception {
        Long id = createCase();
        // resolve API가 아직 없으므로 DB에서 직접 상태를 바꾼다
        jdbcTemplate.update("UPDATE quality_case SET status = 'RESOLVED' WHERE id = ?", id);
        // 실제 요청처럼 별도 영속성 컨텍스트에서 조회하도록 비움
        entityManager.clear();

        mockMvc.perform(delete("/api/quality-cases/{id}", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("해결 완료(RESOLVED)된 품질사례는 삭제할 수 없습니다. id=" + id));
        assertThat(qualityCaseRepository.existsById(id)).isTrue();
    }

    @Test
    void 검사결과가_있는_품질사례_삭제시_409() throws Exception {
        Long id = createCase();
        jdbcTemplate.update("""
                INSERT INTO inspection_result (quality_case_id, inspection_item, text_value, result_status, created_at)
                VALUES (?, '표면 상태', '균열', 'FAIL', NOW())
                """, id);
        entityManager.clear();

        mockMvc.perform(delete("/api/quality-cases/{id}", id))
                .andExpect(status().isConflict());
    }

    @Test
    void 품질사례가_있는_제품은_삭제시_409() throws Exception {
        createCase();
        entityManager.clear();

        mockMvc.perform(delete("/api/products/{id}", productId))
                .andExpect(status().isConflict());
    }
}
