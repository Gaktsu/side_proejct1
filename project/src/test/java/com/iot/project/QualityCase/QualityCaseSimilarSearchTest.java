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
import com.iot.project.qdrant.ScoredPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 실제 모델과 Qdrant 없이 검색 흐름(쿼리 텍스트, RESOLVED 필터, 순서 유지)만 검증한다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QualityCaseSimilarSearchTest {

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
    private Product product;
    private ManufacturingProcess welding;
    private Long openId;

    @BeforeEach
    void setUp() {
        product = productRepository.save(new Product("TEST-P-001", "브레이크 캘리퍼", null, null));
        welding = manufacturingProcessRepository.save(new ManufacturingProcess("TEST-PR-001", "용접", null));
        Equipment equipment = equipmentRepository.save(new Equipment(welding, "TEST-EQ-001", "용접기 1호", null));
        openId = qualityCaseRepository.save(new QualityCase("TEST-QC-OPEN", product, welding, equipment, "LOT-01",
                "용접부 미세 균열", "출하 검사에서 용접부 미세 균열 발견", "크랙", 100, 3,
                LocalDateTime.of(2026, 10, 5, 9, 30))).getId();
        when(embeddingClient.embedQuery(anyString())).thenReturn(vector);
    }

    private Long saveCase(String caseCode, boolean resolved) {
        QualityCase qualityCase = new QualityCase(caseCode, product, welding, null, "LOT-02",
                "용접부 크랙", "접합부 크랙", "크랙", 100, 2, LocalDateTime.of(2026, 9, 1, 10, 0));
        if (resolved) {
            qualityCase.resolve("용접 전류 저하", "전류 설정 복구", null);
        }
        return qualityCaseRepository.save(qualityCase).getId();
    }

    private ResultActions search(Long id) throws Exception {
        return mockMvc.perform(get("/api/quality-cases/{id}/similar-cases", id));
    }

    @Test
    void 쿼리텍스트_임베딩후_Qdrant_순서와_score_그대로_200() throws Exception {
        Long first = saveCase("TEST-QC-R1", true);
        Long second = saveCase("TEST-QC-R2", true);
        // MySQL 조회 결과는 id 순이므로, id가 큰 사례를 1등으로 줘서 Qdrant 순서로 다시 정렬하는지 본다
        when(qdrantClient.search(any(), anyInt()))
                .thenReturn(List.of(new ScoredPoint(second, 0.91), new ScoredPoint(first, 0.85)));

        search(openId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].score").value(0.91))
                .andExpect(jsonPath("$[0].qualityCase.id").value(second))
                .andExpect(jsonPath("$[0].qualityCase.rootCause").value("용접 전류 저하"))
                .andExpect(jsonPath("$[1].score").value(0.85))
                .andExpect(jsonPath("$[1].qualityCase.id").value(first));

        verify(embeddingClient).embedQuery("""
                공정: 용접
                설비: 용접기 1호
                제품: 브레이크 캘리퍼
                증상: 크랙 - 용접부 미세 균열
                관찰: 출하 검사에서 용접부 미세 균열 발견""");
        verify(qdrantClient).search(vector, QualityCaseService.SIMILAR_TOP_K);
    }

    @Test
    void Qdrant가_OPEN_사례나_없는_id를_주면_응답에서_뺀다() throws Exception {
        Long resolved = saveCase("TEST-QC-R1", true);
        Long otherOpen = saveCase("TEST-QC-O2", false);
        // resolve 커밋 실패로 남은 벡터: 다른 OPEN 사례, 검색한 사례 자신, MySQL에 없는 id
        when(qdrantClient.search(any(), anyInt())).thenReturn(List.of(
                new ScoredPoint(otherOpen, 0.95), new ScoredPoint(openId, 0.93),
                new ScoredPoint(999999999L, 0.90), new ScoredPoint(resolved, 0.80)));

        search(openId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].qualityCase.id").value(resolved));
    }

    @Test
    void Qdrant가_비어_있으면_빈_목록_200() throws Exception {
        when(qdrantClient.search(any(), anyInt())).thenReturn(List.of());

        search(openId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void 없는_사례_404() throws Exception {
        search(999999999L).andExpect(status().isNotFound());

        verify(embeddingClient, never()).embedQuery(anyString());
    }

    @Test
    void RESOLVED_사례로_검색하면_409() throws Exception {
        search(saveCase("TEST-QC-R1", true)).andExpect(status().isConflict());

        verify(embeddingClient, never()).embedQuery(anyString());
    }

    @Test
    void 임베딩_실패시_503() throws Exception {
        when(embeddingClient.embedQuery(anyString())).thenThrow(new IllegalStateException("모델 로딩 실패"));

        search(openId).andExpect(status().isServiceUnavailable());
    }

    @Test
    void Qdrant_실패시_503() throws Exception {
        when(qdrantClient.search(any(), anyInt())).thenThrow(new IllegalStateException("Qdrant 연결 실패"));

        search(openId).andExpect(status().isServiceUnavailable());
    }

    @Test
    void 토큰_상한_초과시_400_Qdrant_호출_안함() throws Exception {
        when(embeddingClient.embedQuery(anyString())).thenThrow(new EmbeddingInputTooLongException(1500, 1024));

        search(openId).andExpect(status().isBadRequest());

        verify(qdrantClient, never()).search(any(), anyInt());
    }
}
