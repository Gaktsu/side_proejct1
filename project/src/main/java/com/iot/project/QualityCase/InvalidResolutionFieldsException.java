package com.iot.project.QualityCase;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

// 원인/조치/결과는 OPEN이면 비어 있어야 하고(해결은 /resolve), RESOLVED 수정이면 원인·조치가 필수
public class InvalidResolutionFieldsException extends ResponseStatusException {
    public InvalidResolutionFieldsException(String reason) {
        super(HttpStatus.BAD_REQUEST, reason);
    }
}
