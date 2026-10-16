package com.iot.project.QualityCase;

import com.iot.project.Equipment.Equipment;
import com.iot.project.Equipment.EquipmentRepository;
import com.iot.project.ManufacturingProcess.ManufacturingProcess;
import com.iot.project.ManufacturingProcess.ManufacturingProcessRepository;
import com.iot.project.Product.Product;
import com.iot.project.Product.ProductRepository;
import com.iot.project.embedding.EmbeddingClient;
import com.iot.project.qdrant.QdrantClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 실제 모델(2.27GB)과 Qdrant 없이 resolve 흐름만 검증한다. 롤백 확인은 QualityCaseResolveRollbackTest
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

    @MockitoBean
    EmbeddingClient embeddingClient;

    @MockitoBean
    QdrantClient qdrantClient;

    private final float[] vector = new float[1024];
    private Long caseId;

    @BeforeEach
    void setUp() {
        Product product = productRepository.save(new Product("TEST-P-001", "브레이크 캘리퍼", null, null));
        ManufacturingProcess welding = manufacturingProcessRepository.save(new ManufacturingProcess("TEST-PR-001", "용접", null));
        Equipment equipment = equipmentRepository.save(new Equipment(welding, "TEST-EQ-001", "용접기 1호", null));
        caseId = qualityCaseRepository.save(new QualityCase("TEST-QC-001", product, welding, equipment, "LOT-01",
                "용접부 미세 균열", "출하 검사에서 용접부 미세 균열 발견", "크랙", 100, 3,
                LocalDateTime.of(2026, 10, 5, 9, 30))).getId();
        when(embeddingClient.embedDocument(anyString())).thenReturn(vector);
    }

    private ResultActions resolve(Long id, String body) throws Exception {
        return mockMvc.perform(post("/api/quality-cases/{id}/resolve", id)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static final String BODY = """
            {"rootCause": "용접 전류 저하", "correctiveAction": "전류 설정 복구", "resultSummary": "재발 없음"}
            """;

    @Test
    void 해결시_200_RESOLVED_문서텍스트_임베딩후_적재() throws Exception {
        resolve(caseId, BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.rootCause").value("용접 전류 저하"))
                .andExpect(jsonPath("$.correctiveAction").value("전류 설정 복구"))
                .andExpect(jsonPath("$.resultSummary").value("재발 없음"));

        verify(embeddingClient).embedDocument("""
                공정: 용접
                설비: 용접기 1호
                제품: 브레이크 캘리퍼
                증상: 크랙 - 용접부 미세 균열
                관찰: 출하 검사에서 용접부 미세 균열 발견
                확정 원인: 용접 전류 저하
                조치: 전류 설정 복구""");
        verify(qdrantClient).upsert(caseId, vector);
    }

    @Test
    void 처리결과_없이도_해결_가능() throws Exception {
        resolve(caseId, """
                {"rootCause": "용접 전류 저하", "correctiveAction": "전류 설정 복구"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resultSummary").value(nullValue()));
    }

    @Test
    void 원인_누락시_400_적재_안함() throws Exception {
        resolve(caseId, """
                {"rootCause": " ", "correctiveAction": "전류 설정 복구"}
                """)
                .andExpect(status().isBadRequest());

        verify(qdrantClient, never()).upsert(anyLong(), any());
    }

    @Test
    void 없는_사례_404() throws Exception {
        resolve(999999999L, BODY).andExpect(status().isNotFound());

        verify(qdrantClient, never()).upsert(anyLong(), any());
    }

    @Test
    void 이미_해결된_사례_409_다시_적재_안함() throws Exception {
        resolve(caseId, BODY).andExpect(status().isOk());

        resolve(caseId, BODY).andExpect(status().isConflict());

        verify(qdrantClient, times(1)).upsert(anyLong(), any());
    }
}
