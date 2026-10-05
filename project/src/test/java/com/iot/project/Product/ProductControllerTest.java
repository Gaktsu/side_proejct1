package com.iot.project.Product;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private static final String BODY = """
            {"productCode": "TEST-P-001", "name": "브레이크 캘리퍼", "modelName": "BC-100"}
            """;

    private Long createProduct() throws Exception {
        String json = mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    @Test
    void 제품_생성() throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.productCode").value("TEST-P-001"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void 필수값_누락시_400() throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productCode": "", "name": "이름"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 제품코드_중복시_409() throws Exception {
        createProduct();
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("이미 존재하는 제품 코드입니다. productCode=TEST-P-001"));
    }

    @Test
    void 제품_삭제() throws Exception {
        Long id = createProduct();
        mockMvc.perform(delete("/api/products/{id}", id))
                .andExpect(status().isNoContent());
        assertThat(productRepository.existsById(id)).isFalse();
    }

    @Test
    void 없는_제품_삭제시_404() throws Exception {
        mockMvc.perform(delete("/api/products/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void 품질사례에서_사용중인_제품_삭제시_409() throws Exception {
        Long productId = createProduct();
        jdbcTemplate.update("INSERT INTO manufacturing_process (process_code, name, created_at) VALUES ('TEST-PR-001', '용접', NOW())");
        Long processId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update("""
                INSERT INTO quality_case (case_code, product_id, process_id, lot_no, title, problem_description,
                    defect_type, production_quantity, defect_quantity, status, occurred_at, created_at, updated_at)
                VALUES ('TEST-QC-001', ?, ?, 'LOT-1', '제목', '설명', '크랙', 100, 1, 'OPEN', NOW(), NOW(), NOW())
                """, productId, processId);

        mockMvc.perform(delete("/api/products/{id}", productId))
                .andExpect(status().isConflict());
    }
}
