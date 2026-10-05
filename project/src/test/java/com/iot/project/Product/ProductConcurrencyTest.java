package com.iot.project.Product;

import com.iot.project.common.ConcurrentRequests;
import org.junit.jupiter.api.AfterEach;
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
class ProductConcurrencyTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM product WHERE product_code LIKE 'TEST-%'");
    }

    @Test
    void 같은_제품코드_동시_등록시_하나만_201_나머지는_409() throws Exception {
        List<Integer> statuses = ConcurrentRequests.run(10, () -> mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productCode": "TEST-P-CONC", "name": "브레이크 캘리퍼"}
                                """))
                .andReturn().getResponse().getStatus());

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(9);
    }
}
