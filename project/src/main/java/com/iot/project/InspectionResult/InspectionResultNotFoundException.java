package com.iot.project.InspectionResult;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class InspectionResultNotFoundException extends ResponseStatusException {
    public InspectionResultNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "검사 결과를 찾을 수 없습니다. id=" + id);
    }
}
