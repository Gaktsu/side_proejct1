package com.iot.project.Equipment;

import com.iot.project.ManufacturingProcess.ManufacturingProcess;
import com.iot.project.ManufacturingProcess.ManufacturingProcessRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EquipmentControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EquipmentRepository equipmentRepository;

    @Autowired
    ManufacturingProcessRepository manufacturingProcessRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private static final String BODY = """
            {"equipmentCode": "TEST-EQ-001", "name": "용접기 1호"}
            """;

    private Long processId;

    @BeforeEach
    void setUp() {
        processId = manufacturingProcessRepository.save(new ManufacturingProcess("TEST-PR-001", "용접", null)).getId();
    }

    private Long createEquipment() throws Exception {
        String json = mockMvc.perform(post("/api/processes/{id}/equipments", processId)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    @Test
    void 설비_생성() throws Exception {
        mockMvc.perform(post("/api/processes/{id}/equipments", processId)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.processId").value(processId))
                .andExpect(jsonPath("$.equipmentCode").value("TEST-EQ-001"));
    }

    @Test
    void 없는_공정에_설비_생성시_404() throws Exception {
        mockMvc.perform(post("/api/processes/{id}/equipments", Long.MAX_VALUE)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void 필수값_누락시_400() throws Exception {
        mockMvc.perform(post("/api/processes/{id}/equipments", processId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "용접기 1호"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 설비코드_중복시_409() throws Exception {
        createEquipment();
        mockMvc.perform(post("/api/processes/{id}/equipments", processId)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void 설비_삭제() throws Exception {
        Long id = createEquipment();
        mockMvc.perform(delete("/api/equipments/{id}", id))
                .andExpect(status().isNoContent());
        assertThat(equipmentRepository.existsById(id)).isFalse();
    }

    @Test
    void 없는_설비_삭제시_404() throws Exception {
        mockMvc.perform(delete("/api/equipments/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void 품질사례에서_사용중인_설비_삭제시_409() throws Exception {
        Long equipmentId = createEquipment();
        jdbcTemplate.update("INSERT INTO product (product_code, name, created_at) VALUES ('TEST-P-001', '캘리퍼', NOW())");
        Long productId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update("""
                INSERT INTO quality_case (case_code, product_id, process_id, equipment_id, lot_no, title,
                    problem_description, defect_type, production_quantity, defect_quantity, status,
                    occurred_at, created_at, updated_at)
                VALUES ('TEST-QC-001', ?, ?, ?, 'LOT-1', '제목', '설명', '크랙', 100, 1, 'OPEN', NOW(), NOW(), NOW())
                """, productId, processId, equipmentId);

        mockMvc.perform(delete("/api/equipments/{id}", equipmentId))
                .andExpect(status().isConflict());
    }
}
