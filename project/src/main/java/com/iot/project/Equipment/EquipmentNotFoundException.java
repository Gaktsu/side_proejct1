package com.iot.project.Equipment;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class EquipmentNotFoundException extends ResponseStatusException {
    public EquipmentNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "설비를 찾을 수 없습니다. id=" + id);
    }
}
