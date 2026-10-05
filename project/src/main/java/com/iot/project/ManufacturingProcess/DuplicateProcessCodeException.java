package com.iot.project.ManufacturingProcess;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class DuplicateProcessCodeException extends ResponseStatusException {
    public DuplicateProcessCodeException(String processCode) {
        super(HttpStatus.CONFLICT, "이미 존재하는 공정 코드입니다. processCode=" + processCode);
    }
}
