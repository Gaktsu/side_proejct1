package com.iot.project.Equipment;

import com.iot.project.ManufacturingProcess.ManufacturingProcess;
import com.iot.project.ManufacturingProcess.ManufacturingProcessRepository;
import com.iot.project.common.ConcurrentRequests;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// 각 요청이 실제로 커밋되어야 경쟁이 일어나므로 @Transactional을 붙이지 않고 직접 정리한다
@SpringBootTest
@AutoConfigureMockMvc
class EquipmentConcurrencyTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ManufacturingProcessRepository manufacturingProcessRepository;

    private Long processId;

    @BeforeEach
    void setUp() {
        processId = manufacturingProcessRepository.save(new ManufacturingProcess("TEST-PR-CONC", "용접", null)).getId();
    }

    @AfterEach
    void cleanUp() {
        // 자식(equipment)부터 지워야 FK에 걸리지 않는다
        jdbcTemplate.update("DELETE FROM equipment WHERE equipment_code LIKE 'TEST-%'");
        jdbcTemplate.update("DELETE FROM manufacturing_process WHERE process_code LIKE 'TEST-%'");
    }

    @Test
    void 같은_설비코드_동시_등록시_하나만_201_나머지는_409() throws Exception {
        List<Integer> statuses = ConcurrentRequests.run(10, () -> mockMvc.perform(post("/api/processes/{id}/equipments", processId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"equipmentCode": "TEST-EQ-CONC", "name": "용접기 1호"}
                                """))
                .andReturn().getResponse().getStatus());

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(9);
    }
}
