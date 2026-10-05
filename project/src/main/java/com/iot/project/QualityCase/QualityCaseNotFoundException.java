package com.iot.project.QualityCase;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class QualityCaseNotFoundException extends ResponseStatusException {
    public QualityCaseNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "품질사례를 찾을 수 없습니다. id=" + id);
    }
}
