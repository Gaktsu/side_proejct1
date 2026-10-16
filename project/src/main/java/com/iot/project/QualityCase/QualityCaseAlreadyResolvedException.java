package com.iot.project.QualityCase;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class QualityCaseAlreadyResolvedException extends ResponseStatusException {
    public QualityCaseAlreadyResolvedException(Long id) {
        super(HttpStatus.CONFLICT, "이미 해결 완료(RESOLVED)된 품질사례입니다. id=" + id);
    }
}
