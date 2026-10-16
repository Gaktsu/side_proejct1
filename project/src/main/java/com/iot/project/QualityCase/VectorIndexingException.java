package com.iot.project.QualityCase;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

// Embedding 또는 Qdrant 적재 실패. 트랜잭션이 롤백되어 사례는 OPEN으로 남는다
public class VectorIndexingException extends ResponseStatusException {
    public VectorIndexingException(Long id, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "검색용 벡터 적재에 실패해 해결 처리하지 않았습니다. 잠시 후 다시 시도해 주세요. id=" + id, cause);
    }
}
