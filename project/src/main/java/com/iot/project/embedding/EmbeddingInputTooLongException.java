package com.iot.project.embedding;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

// 입력이 토큰 상한을 넘으면 잘라서 임베딩하지 않고 거절한다 (잘리면 뒷부분이 검색에 반영되지 않음)
public class EmbeddingInputTooLongException extends ResponseStatusException {
    public EmbeddingInputTooLongException(int tokens, int maxTokens) {
        super(HttpStatus.BAD_REQUEST, "임베딩할 텍스트가 최대 " + maxTokens + "토큰을 넘습니다 (현재 " + tokens
                + "토큰). 문제 설명이나 원인·조치 내용을 줄여 주세요.");
    }
}
