package com.iot.project.ManufacturingProcess;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ManufacturingProcessControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ManufacturingProcessRepository manufacturingProcessRepository;

    @Autowired
    EntityManager entityManager;

    private static final String BODY = """
            {"processCode": "TEST-PR-001", "name": "용접", "description": "스팟 용접 공정"}
            """;

    private Long createProcess() throws Exception {
        String json = mockMvc.perform(post("/api/processes").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    @Test
    void 공정_생성() throws Exception {
        mockMvc.perform(post("/api/processes").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.processCode").value("TEST-PR-001"))
                .andExpect(jsonPath("$.name").value("용접"));
    }

    @Test
    void 필수값_누락시_400() throws Exception {
        mockMvc.perform(post("/api/processes").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"processCode": "TEST-PR-001"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 공정코드_중복시_409() throws Exception {
        createProcess();
        mockMvc.perform(post("/api/processes").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void 공정_삭제() throws Exception {
        Long id = createProcess();
        mockMvc.perform(delete("/api/processes/{id}", id))
                .andExpect(status().isNoContent());
        assertThat(manufacturingProcessRepository.existsById(id)).isFalse();
    }

    @Test
    void 없는_공정_삭제시_404() throws Exception {
        mockMvc.perform(delete("/api/processes/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void 설비가_있는_공정_삭제시_409() throws Exception {
        Long id = createProcess();
        mockMvc.perform(post("/api/processes/{id}/equipments", id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"equipmentCode": "TEST-EQ-001", "name": "용접기 1호"}
                                """))
                .andExpect(status().isCreated());
        // 실제 요청처럼 별도 영속성 컨텍스트에서 삭제하도록 비움
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(delete("/api/processes/{id}", id))
                .andExpect(status().isConflict());
    }

    @Test
    void 공정_목록_조회() throws Exception {
        createProcess();
        mockMvc.perform(get("/api/processes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].processCode").value(hasItem("TEST-PR-001")));
    }

    @Test
    void 공정_단건_조회() throws Exception {
        Long id = createProcess();
        mockMvc.perform(get("/api/processes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("용접"));
    }

    @Test
    void 없는_공정_조회시_404() throws Exception {
        mockMvc.perform(get("/api/processes/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void 공정_수정시_보낸_값으로_교체되고_코드는_유지() throws Exception {
        Long id = createProcess();
        mockMvc.perform(patch("/api/processes/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "아크 용접"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processCode").value("TEST-PR-001"))
                .andExpect(jsonPath("$.name").value("아크 용접"))
                .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void 없는_공정_수정시_404() throws Exception {
        mockMvc.perform(patch("/api/processes/{id}", Long.MAX_VALUE).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "아크 용접"}
                                """))
                .andExpect(status().isNotFound());
    }
}
