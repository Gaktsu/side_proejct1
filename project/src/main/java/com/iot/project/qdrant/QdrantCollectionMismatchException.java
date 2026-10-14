package com.iot.project.qdrant;

// 기존 컬렉션의 벡터 설정이 현재 임베딩 규칙(1024차원, Cosine)과 다를 때. 기동을 중단시킨다
public class QdrantCollectionMismatchException extends IllegalStateException {
    public QdrantCollectionMismatchException(String collection, int size, String distance) {
        super("Qdrant 컬렉션 설정이 다릅니다. collection=" + collection
                + ", 현재=" + size + "/" + distance
                + ", 기대=" + QdrantClient.VECTOR_SIZE + "/" + QdrantClient.DISTANCE
                + " (임베딩 모델이 바뀌었다면 컬렉션을 삭제하고 전체 재적재해야 합니다)");
    }
}
