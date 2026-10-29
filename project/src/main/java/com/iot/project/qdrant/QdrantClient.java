package com.iot.project.qdrant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Qdrant REST API 호출. 컬렉션 하나(quality_case_vectors)를 대상으로 하며 Point ID = QualityCase.id
@Component
public class QdrantClient {

    static final int VECTOR_SIZE = 1024;
    static final String DISTANCE = "Cosine";
    // payload가 없는 옛 포인트(LLD 1 이전 적재분)도 이 조건에 걸려 검색에서 빠진다
    private static final Map<String, Object> SEARCHABLE_ONLY =
            Map.of("must", List.of(Map.of("key", "searchable", "match", Map.of("value", true))));

    private final RestClient restClient;
    private final String collection;
    private volatile boolean collectionReady;

    public QdrantClient(@Value("${qdrant.url}") String url,
                        @Value("${qdrant.collection:quality_case_vectors}") String collection) {
        this.restClient = RestClient.builder().baseUrl(url).build();
        this.collection = collection;
    }

    // 컬렉션이 없으면 생성하고, 있으면 벡터 설정이 같은지 확인한다
    public void ensureCollection() {
        boolean exists = restClient.get()
                .uri("/collections/{name}/exists", collection)
                .retrieve()
                .body(ExistsResponse.class)
                .result().exists();

        if (!exists) {
            restClient.put()
                    .uri("/collections/{name}", collection)
                    .body(Map.of("vectors", new VectorParams(VECTOR_SIZE, DISTANCE)))
                    .retrieve()
                    .toBodilessEntity();
            return;
        }

        VectorParams vectors = restClient.get()
                .uri("/collections/{name}", collection)
                .retrieve()
                .body(CollectionInfoResponse.class)
                .result().config().params().vectors();
        if (vectors.size() != VECTOR_SIZE || !DISTANCE.equals(vectors.distance())) {
            throw new QdrantCollectionMismatchException(collection, vectors.size(), vectors.distance());
        }
    }

    // 첫 사용 시 1회 확인 (앱 기동과 분리). 실패하면 false로 남아 다음 호출 때 다시 확인한다
    private void ensureReady() {
        if (collectionReady) return;
        synchronized (this) {
            if (collectionReady) return;
            ensureCollection();
            collectionReady = true;
        }
    }

    // 같은 id가 있으면 덮어쓴다(payload 포함). 항상 searchable=false로 넣고, 검색 노출은 setSearchable만 바꾼다
    // (searchable을 쓰는 곳을 SearchVisibilityWorker 하나로 모아 스레드 간 덮어쓰기 경합을 없앤다)
    public void upsert(long id, float[] vector, int sourceVersion) {
        ensureReady();
        restClient.put()
                .uri("/collections/{name}/points?wait=true", collection)
                .body(Map.of("points", List.of(new Point(id, vector, new PointPayload(sourceVersion, false)))))
                .retrieve()
                .toBodilessEntity();
    }

    // id로 고르는 대신 필터로 골라, 포인트가 없거나 버전이 다르면 오류 없이 0건 갱신으로 끝난다
    // true는 포인트의 sourceVersion이 requiredVersion과 같을 때만 (옛 벡터를 다시 검색에 쓰지 않음)
    public void setSearchable(long id, boolean searchable, int requiredVersion) {
        ensureReady();
        List<Map<String, Object>> must = new ArrayList<>(List.of(Map.of("has_id", List.of(id))));
        if (searchable) {
            must.add(Map.of("key", "sourceVersion", "match", Map.of("value", requiredVersion)));
        }
        restClient.post()
                .uri("/collections/{name}/points/payload?wait=true", collection)
                .body(Map.of("payload", Map.of("searchable", searchable), "filter", Map.of("must", must)))
                .retrieve()
                .toBodilessEntity();
    }

    public List<ScoredPoint> search(float[] vector, int limit) {
        ensureReady();
        return restClient.post()
                .uri("/collections/{name}/points/query", collection)
                .body(Map.of("query", vector, "limit", limit, "filter", SEARCHABLE_ONLY, "with_payload", true))
                .retrieve()
                .body(QueryResponse.class)
                .result().points();
    }

    public void delete(long id) {
        ensureReady();
        restClient.post()
                .uri("/collections/{name}/points/delete?wait=true", collection)
                .body(Map.of("points", List.of(id)))
                .retrieve()
                .toBodilessEntity();
    }

    // Qdrant 요청/응답 JSON 중 사용하는 필드만 매핑
    private record VectorParams(int size, String distance) {}
    private record Point(long id, float[] vector, PointPayload payload) {}

    private record ExistsResponse(Exists result) {}
    private record Exists(boolean exists) {}

    private record CollectionInfoResponse(CollectionInfo result) {}
    private record CollectionInfo(CollectionConfig config) {}
    private record CollectionConfig(CollectionParams params) {}
    private record CollectionParams(VectorParams vectors) {}

    private record QueryResponse(QueryResult result) {}
    private record QueryResult(List<ScoredPoint> points) {}
}
