package com.iot.project.AnalysisCandidate;

// Qdrant가 돌려줬지만 근거로 쓸 수 없는 후보의 사유. 이 순서대로 확인해 처음 걸린 하나만 기록한다
public enum InvalidReason {
    DELETED,
    // 해결 취소되어 OPEN
    NOT_RESOLVED,
    // 벡터가 옛 버전 텍스트로 만들어짐 (수정 뒤 재인덱싱 전)
    STALE_VERSION
}
