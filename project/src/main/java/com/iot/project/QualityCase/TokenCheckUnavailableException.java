package com.iot.project.QualityCase;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

// 토크나이저를 불러오지 못해 1024토큰 검사를 못 함 (모델 경로 없음 등). 트랜잭션이 롤백되어 아무것도 바뀌지 않는다
public class TokenCheckUnavailableException extends ResponseStatusException {
    public TokenCheckUnavailableException(Long id, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "검색용 텍스트 길이를 검사하지 못해 처리하지 않았습니다. 잠시 후 다시 시도해 주세요. id=" + id, cause);
    }
}
