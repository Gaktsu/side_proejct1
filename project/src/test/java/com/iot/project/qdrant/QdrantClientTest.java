package com.iot.project.qdrant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

// 실제 Qdrant(localhost:6333)에 대해 검증. 운영 컬렉션 대신 테스트 전용 컬렉션을 쓰고 매번 지운다
@SpringBootTest(properties = "qdrant.collection=" + QdrantClientTest.COLLECTION)
class QdrantClientTest {

    static final String COLLECTION = "test_quality_case_vectors";

    @Autowired
    QdrantClient qdrantClient;

    @Value("${qdrant.url}")
    String qdrantUrl;

    RestClient raw;

    @BeforeEach
    void setUp() {
        raw = RestClient.builder().baseUrl(qdrantUrl).build();
        dropCollection();
        qdrantClient.ensureCollection();
    }

    @AfterEach
    void tearDown() {
        dropCollection();
    }

    // index 위치만 1인 1024차원 벡터. 서로 다른 index끼리는 코사인 유사도 0
    private static float[] vector(int index) {
        float[] v = new float[QdrantClient.VECTOR_SIZE];
        v[index] = 1f;
        return v;
    }

    private void dropCollection() {
        raw.delete().uri("/collections/{name}", COLLECTION).retrieve().toBodilessEntity();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> collectionInfo() {
        Map<String, Object> body = raw.get().uri("/collections/{name}", COLLECTION)
                .retrieve().body(Map.class);
        return (Map<String, Object>) body.get("result");
    }

    @SuppressWarnings("unchecked")
    private int pointCount() {
        Map<String, Object> body = raw.post().uri("/collections/{name}/points/count", COLLECTION)
                .body(Map.of("exact", true))
                .retrieve().body(Map.class);
        return (int) ((Map<String, Object>) body.get("result")).get("count");
    }

    @Test
    @SuppressWarnings("unchecked")
    void 컬렉션_없으면_1024_Cosine으로_생성() {
        Map<String, Object> config = (Map<String, Object>) collectionInfo().get("config");
        Map<String, Object> params = (Map<String, Object>) config.get("params");
        Map<String, Object> vectors = (Map<String, Object>) params.get("vectors");

        assertThat(vectors.get("size")).isEqualTo(1024);
        assertThat(vectors.get("distance")).isEqualTo("Cosine");
    }

    @Test
    void 컬렉션_이미_있으면_데이터_유지() {
        qdrantClient.upsert(1L, vector(0), 1);

        qdrantClient.ensureCollection();

        assertThat(pointCount()).isEqualTo(1);
    }

    @Test
    void 첫_사용_시_컬렉션_자동_생성() {
        dropCollection();
        // 공유 빈은 이전 테스트에서 이미 확인됨(collectionReady=true) → 새 인스턴스로 첫 사용 상황을 만든다
        QdrantClient fresh = new QdrantClient(qdrantUrl, COLLECTION);

        fresh.upsert(1L, vector(0), 1);

        assertThat(pointCount()).isEqualTo(1);
    }

    @Test
    void 컬렉션_설정이_다르면_예외() {
        dropCollection();
        raw.put().uri("/collections/{name}", COLLECTION)
                .body(Map.of("vectors", Map.of("size", 4, "distance", "Cosine")))
                .retrieve().toBodilessEntity();

        assertThatThrownBy(() -> qdrantClient.ensureCollection())
                .isInstanceOf(QdrantCollectionMismatchException.class);
    }

    @Test
    void 같은_벡터로_검색하면_1위_점수_1() {
        qdrantClient.upsert(1L, vector(0), 1);
        qdrantClient.upsert(2L, vector(1), 1);

        List<ScoredPoint> result = qdrantClient.search(vector(0), 5);

        assertThat(result.getFirst().id()).isEqualTo(1L);
        assertThat(result.getFirst().score()).isCloseTo(1.0, within(1e-6));
        assertThat(result.getFirst().payload()).isEqualTo(new PointPayload(1, true));
    }

    @Test
    void payload_없는_옛_포인트는_검색에서_빠짐() {
        qdrantClient.upsert(1L, vector(0), 1);
        // LLD 1 이전 방식(벡터만) 적재
        raw.put().uri("/collections/{name}/points?wait=true", COLLECTION)
                .body(Map.of("points", List.of(Map.of("id", 2L, "vector", vector(0)))))
                .retrieve().toBodilessEntity();

        assertThat(qdrantClient.search(vector(0), 5))
                .extracting(ScoredPoint::id)
                .containsExactly(1L);
    }

    @Test
    void 같은_id로_upsert하면_덮어씀() {
        qdrantClient.upsert(1L, vector(0), 1);
        qdrantClient.upsert(1L, vector(1), 1);

        assertThat(pointCount()).isEqualTo(1);
        assertThat(qdrantClient.search(vector(1), 1).getFirst().score()).isCloseTo(1.0, within(1e-6));
    }

    @Test
    void 삭제하면_검색결과에_없음() {
        qdrantClient.upsert(1L, vector(0), 1);
        qdrantClient.upsert(2L, vector(1), 1);

        qdrantClient.delete(1L);

        assertThat(qdrantClient.search(vector(0), 5))
                .extracting(ScoredPoint::id)
                .containsExactly(2L);
    }

    @Test
    void limit만큼만_반환() {
        qdrantClient.upsert(1L, vector(0), 1);
        qdrantClient.upsert(2L, vector(1), 1);
        qdrantClient.upsert(3L, vector(2), 1);

        assertThat(qdrantClient.search(vector(0), 2)).hasSize(2);
    }
}
