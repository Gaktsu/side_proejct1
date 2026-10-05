package com.iot.project.ManufacturingProcess;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ManufacturingProcessNotFoundException extends ResponseStatusException {
    public ManufacturingProcessNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "공정을 찾을 수 없습니다. id=" + id);
    }
}
