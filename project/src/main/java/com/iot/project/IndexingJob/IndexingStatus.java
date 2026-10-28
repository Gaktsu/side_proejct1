package com.iot.project.IndexingJob;

// embedding_status / qdrant_status 공용. WAITING은 qdrant만 쓴다 (임베딩 완료를 기다림)
// SUPERSEDED: 처리 전에 사례가 수정되어 옛 버전이 된 job. 건너뛴 단계에 남겨 어디까지 갔는지 추적한다
public enum IndexingStatus {
    WAITING,
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    SUPERSEDED
}
