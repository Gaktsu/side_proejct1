package com.iot.project.QualityCase;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class QualityCaseInUseException extends ResponseStatusException {
    public QualityCaseInUseException(Long id) {
        super(HttpStatus.CONFLICT, "검사결과 또는 AI 분석 기록이 있는 품질사례는 삭제할 수 없습니다. id=" + id);
    }
}
