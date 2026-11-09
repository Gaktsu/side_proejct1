package com.iot.project.Analysis;

// PENDING → SEARCHING → CANDIDATES_READY | NO_CANDIDATES | FAILED
// JEV/LLM 단계에서 ANALYZING·COMPLETED 등을 다시 추가한다
public enum AnalysisStatus {
    PENDING,
    SEARCHING,
    // 근거로 쓸 수 있는 후보(invalid_reason NULL)가 1개 이상
    CANDIDATES_READY,
    // 근거로 쓸 후보 0개. 장애가 아닌 정상 결과
    NO_CANDIDATES,
    // 임베딩·Qdrant 실패 등. 자동 재시도 없음
    FAILED;

    // 진행 중이면 재요청을 막고(409), 끝났으면 재요청 시 지우고 새로 만든다
    public boolean isInProgress() {
        return this == PENDING || this == SEARCHING;
    }
}
