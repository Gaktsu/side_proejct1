package com.iot.project.Product;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class DuplicateProductCodeException extends ResponseStatusException {
    public DuplicateProductCodeException(String productCode) {
        super(HttpStatus.CONFLICT, "이미 존재하는 제품 코드입니다. productCode=" + productCode);
    }
}
