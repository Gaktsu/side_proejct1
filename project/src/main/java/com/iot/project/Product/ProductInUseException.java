package com.iot.project.Product;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ProductInUseException extends ResponseStatusException {
    public ProductInUseException(Long id) {
        super(HttpStatus.CONFLICT, "품질사례에서 사용 중인 제품은 삭제할 수 없습니다. id=" + id);
    }
}
