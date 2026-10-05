package com.iot.project.ManufacturingProcess;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ManufacturingProcessInUseException extends ResponseStatusException {
    public ManufacturingProcessInUseException(Long id) {
        super(HttpStatus.CONFLICT, "설비 또는 품질사례에서 사용 중인 공정은 삭제할 수 없습니다. id=" + id);
    }
}
