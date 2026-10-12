package com.iot.project.QualityCase;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ResolvedQualityCaseUpdateException extends ResponseStatusException {
    public ResolvedQualityCaseUpdateException(Long id) {
        super(HttpStatus.CONFLICT, "해결 완료(RESOLVED)된 품질사례는 수정할 수 없습니다. id=" + id);
    }
}
