package com.iot.project.embedding;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 모델 파일이 없어도 앱은 기동되고, 임베딩 호출만 실패하는지 검증 (실제 모델은 쓰지 않음)
@SpringBootTest(properties = "embedding.model-dir=/nonexistent/pixie-rune-onnx")
class EmbeddingModelMissingTest {

    @Autowired
    EmbeddingClient embeddingClient;

    @Test
    void 모델_없어도_기동되고_호출만_실패() {
        assertThatThrownBy(() -> embeddingClient.embedQuery("보압 저하"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("임베딩 모델 로딩 실패");
    }
}
