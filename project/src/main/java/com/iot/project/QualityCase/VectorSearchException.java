package com.iot.project.QualityCase;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

// Embedding 또는 Qdrant 검색 실패. 조회만 하므로 DB 변경은 없다
public class VectorSearchException extends ResponseStatusException {
    public VectorSearchException(Long id, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "유사 사례 검색에 실패했습니다. 잠시 후 다시 시도해 주세요. id=" + id, cause);
    }
}
