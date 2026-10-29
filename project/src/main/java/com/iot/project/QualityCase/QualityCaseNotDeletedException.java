package com.iot.project.QualityCase;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class QualityCaseNotDeletedException extends ResponseStatusException {
    public QualityCaseNotDeletedException(Long id) {
        super(HttpStatus.CONFLICT, "삭제된 품질사례만 복구할 수 있습니다. id=" + id);
    }
}
