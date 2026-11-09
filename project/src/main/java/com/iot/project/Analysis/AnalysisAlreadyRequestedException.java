package com.iot.project.Analysis;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

// 같은 사례의 분석이 아직 진행 중(PENDING·SEARCHING). 응답에 기존 analysisId를 담아 그 분석을 조회하게 한다
public class AnalysisAlreadyRequestedException extends ResponseStatusException {
    public AnalysisAlreadyRequestedException(Long qualityCaseId, Long analysisId) {
        super(HttpStatus.CONFLICT, "이미 등록된 분석 요청입니다. qualityCaseId=" + qualityCaseId + ", analysisId=" + analysisId);
        getBody().setProperty("analysisId", analysisId);
    }
}
