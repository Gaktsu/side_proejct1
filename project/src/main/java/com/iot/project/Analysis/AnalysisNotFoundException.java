package com.iot.project.Analysis;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

// 없거나 대상 사례가 삭제된 분석 (삭제된 사례는 restore 외 모든 API에서 404)
public class AnalysisNotFoundException extends ResponseStatusException {
    public AnalysisNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "분석을 찾을 수 없습니다. id=" + id);
    }
}
