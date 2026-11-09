package com.iot.project.Analysis;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ResolvedQualityCaseAnalysisException extends ResponseStatusException {
    public ResolvedQualityCaseAnalysisException(Long id) {
        super(HttpStatus.CONFLICT, "분석은 OPEN 품질사례만 요청할 수 있습니다. id=" + id);
    }
}
