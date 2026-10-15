package com.iot.project.qdrant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Qdrant가 꺼져 있어도 앱은 기동되고, Qdrant 호출만 실패하는지 검증 (닫힌 포트로 연결 실패 재현)
@SpringBootTest(properties = "qdrant.url=http://localhost:1")
class QdrantDownTest {

    @Autowired
    QdrantClient qdrantClient;

    @Test
    void Qdrant_꺼져도_기동되고_호출만_실패() {
        assertThatThrownBy(() -> qdrantClient.search(new float[QdrantClient.VECTOR_SIZE], 5))
                .isInstanceOf(ResourceAccessException.class);
    }
}
