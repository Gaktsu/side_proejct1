package com.iot.project.QualityCase;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class QualityCaseNotResolvedException extends ResponseStatusException {
    public QualityCaseNotResolvedException(Long id) {
        super(HttpStatus.CONFLICT, "해결 완료(RESOLVED)된 품질사례만 해결 취소할 수 있습니다. id=" + id);
    }
}
