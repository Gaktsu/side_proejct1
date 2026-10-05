package com.iot.project.Equipment;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class DuplicateEquipmentCodeException extends ResponseStatusException {
    public DuplicateEquipmentCodeException(String equipmentCode) {
        super(HttpStatus.CONFLICT, "이미 존재하는 설비 코드입니다. equipmentCode=" + equipmentCode);
    }
}
