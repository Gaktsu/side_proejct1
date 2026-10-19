package com.iot.project.QualityCase;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ResolvedQualityCaseSearchException extends ResponseStatusException {
    public ResolvedQualityCaseSearchException(Long id) {
        super(HttpStatus.CONFLICT, "유사 사례 검색은 OPEN 품질사례만 가능합니다. id=" + id);
    }
}
