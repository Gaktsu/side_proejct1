package com.iot.project.QualityCase;

import com.iot.project.Equipment.Equipment;
import com.iot.project.Equipment.EquipmentRepository;
import com.iot.project.ManufacturingProcess.ManufacturingProcess;
import com.iot.project.ManufacturingProcess.ManufacturingProcessRepository;
import com.iot.project.Product.Product;
import com.iot.project.Product.ProductRepository;
import com.iot.project.embedding.EmbeddingClient;
import com.iot.project.embedding.EmbeddingInputTooLongException;
import com.iot.project.qdrant.QdrantClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 해결과 RESOLVED 수정은 MySQL에 기록만 하고 바로 응답한다 (임베딩·Qdrant는 IndexingWorkerTest)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QualityCaseResolveTest {

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

    @MockitoBean
    EmbeddingClient embeddingClient;

    @MockitoBean
    QdrantClient qdrantClient;

    private Long caseId;
    private Long productId;
    private Long processId;

    @BeforeEach
    void setUp() {
        Product product = productRepository.save(new Product("TEST-P-001", "브레이크 캘리퍼", null, null));
        ManufacturingProcess welding = manufacturingProcessRepository.save(new ManufacturingProcess("TEST-PR-001", "용접", null));
        Equipment equipment = equipmentRepository.save(new Equipment(welding, "TEST-EQ-001", "용접기 1호", null));
        productId = product.getId();
        processId = welding.getId();
        caseId = qualityCaseRepository.save(new QualityCase("TEST-QC-001", product, welding, equipment, "LOT-01",
                "용접부 미세 균열", "출하 검사에서 용접부 미세 균열 발견", "크랙", 100, 3,
                LocalDateTime.of(2026, 10, 5, 9, 30))).getId();
    }

    private ResultActions resolve(Long id, String body) throws Exception {
        return mockMvc.perform(post("/api/quality-cases/{id}/resolve", id)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions updateCase(Long id, String resolutionFields) throws Exception {
        return mockMvc.perform(put("/api/quality-cases/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "productId": %d, "processId": %d, "equipmentId": null,
                          "lotNo": "LOT-01", "title": "용접부 균열 (재확인)",
                          "problemDescription": "출하 검사에서 용접부 미세 균열 발견",
                          "defectType": "크랙", "productionQuantity": 100, "defectQuantity": 3,
                          "occurredAt": "2026-10-05T09:30:00" %s
                        }
                        """.formatted(productId, processId, resolutionFields)));
    }

    private int jobCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM indexing_job WHERE quality_case_id = ?", Integer.class, caseId);
    }

    private static final String BODY = """
            {"rootCause": "용접 전류 저하", "correctiveAction": "전류 설정 복구", "resultSummary": "재발 없음"}
            """;

    @Test
    void 해결시_200_RESOLVED_버전1_job_대기_임베딩은_안함() throws Exception {
        resolve(caseId, BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.rootCause").value("용접 전류 저하"))
                .andExpect(jsonPath("$.sourceVersion").value(1))
                .andExpect(jsonPath("$.embeddingStatus").value("PENDING"))
                .andExpect(jsonPath("$.qdrantStatus").value("WAITING"));

        // 토큰 수만 동기로 검사하고, 임베딩·Qdrant는 처리기가 커밋 뒤에 한다
        verify(embeddingClient).checkDocumentLength("""
                공정: 용접
                설비: 용접기 1호
                제품: 브레이크 캘리퍼
                증상: 크랙 - 용접부 미세 균열
                관찰: 출하 검사에서 용접부 미세 균열 발견
                확정 원인: 용접 전류 저하
                조치: 전류 설정 복구""");
        verify(embeddingClient, never()).embedDocument(anyString());
        verifyNoInteractions(qdrantClient);
        assertThat(jobCount()).isEqualTo(1);
    }

    @Test
    void 원인_누락시_400_job_없음() throws Exception {
        resolve(caseId, """
                {"rootCause": " ", "correctiveAction": "전류 설정 복구"}
                """)
                .andExpect(status().isBadRequest());

        assertThat(jobCount()).isZero();
    }

    @Test
    void 이미_해결된_사례_409_job_추가_없음() throws Exception {
        resolve(caseId, BODY).andExpect(status().isOk());

        resolve(caseId, BODY).andExpect(status().isConflict());

        assertThat(jobCount()).isEqualTo(1);
    }

    @Test
    void 토큰_상한_초과시_400_job_없음() throws Exception {
        doThrow(new EmbeddingInputTooLongException(1500, 1024)).when(embeddingClient).checkDocumentLength(anyString());

        resolve(caseId, BODY).andExpect(status().isBadRequest());

        assertThat(jobCount()).isZero();
    }

    @Test
    void 해결된_사례_수정시_직전값은_이력에_버전2_새_job() throws Exception {
        resolve(caseId, BODY).andExpect(status().isOk());

        updateCase(caseId, """
                , "rootCause": "용접 와이어 불량", "correctiveAction": "와이어 교체", "resultSummary": null""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.rootCause").value("용접 와이어 불량"))
                .andExpect(jsonPath("$.equipmentId").doesNotExist())
                .andExpect(jsonPath("$.sourceVersion").value(2))
                .andExpect(jsonPath("$.embeddingStatus").value("PENDING"));

        mockMvc.perform(get("/api/quality-cases/{id}/revisions", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].version").value(1))
                .andExpect(jsonPath("$[0].rootCause").value("용접 전류 저하"))
                .andExpect(jsonPath("$[0].title").value("용접부 미세 균열"))
                .andExpect(jsonPath("$[0].equipmentId").exists());
        assertThat(jobCount()).isEqualTo(2);
    }

    @Test
    void OPEN_사례_수정에_원인을_보내면_400() throws Exception {
        updateCase(caseId, """
                , "rootCause": "용접 전류 저하", "correctiveAction": "전류 설정 복구\"""")
                .andExpect(status().isBadRequest());

        assertThat(jobCount()).isZero();
    }
}
