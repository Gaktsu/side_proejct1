package com.iot.project.QualityCase;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class InvalidDefectQuantityException extends ResponseStatusException {
    public InvalidDefectQuantityException(Integer productionQuantity, Integer defectQuantity) {
        super(HttpStatus.BAD_REQUEST, "불량 수량은 생산 수량보다 클 수 없습니다. productionQuantity=" + productionQuantity + ", defectQuantity=" + defectQuantity);
    }
}
