package com.iot.project.QualityCase;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class EquipmentProcessMismatchException extends ResponseStatusException {
    public EquipmentProcessMismatchException(Long equipmentId, Long processId) {
        super(HttpStatus.BAD_REQUEST, "선택한 설비가 선택한 공정에 속하지 않습니다. equipmentId=" + equipmentId + ", processId=" + processId);
    }
}
