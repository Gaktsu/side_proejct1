package com.iot.project.InspectionResult;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class InvalidInspectionResultException extends ResponseStatusException {
    public InvalidInspectionResultException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
