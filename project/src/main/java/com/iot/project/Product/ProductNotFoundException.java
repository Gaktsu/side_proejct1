package com.iot.project.Product;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ProductNotFoundException extends ResponseStatusException {
    public ProductNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "제품을 찾을 수 없습니다. id=" + id);
    }
}
