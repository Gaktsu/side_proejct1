package com.iot.project.Equipment;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class EquipmentInUseException extends ResponseStatusException {
    public EquipmentInUseException(Long id) {
        super(HttpStatus.CONFLICT, "품질사례에서 사용 중인 설비는 삭제할 수 없습니다. id=" + id);
    }
}
