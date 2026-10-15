package com.iot.project.embedding;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;

// PIXIE-Rune(ONNX)로 텍스트를 1024차원 정규화 벡터로 변환. 모델 파일은 scripts/export_pixie_onnx.py로 만든다
// ONNX 출력이 이미 CLS + L2 정규화된 최종 벡터라 Java는 결과를 그대로 반환한다
@Slf4j
@Component
public class EmbeddingClient {

    static final int MAX_TOKENS = 6144;
    private static final String QUERY_PREFIX = "query: ";

    private final String modelDir;
    private volatile Model model;

    private record Model(HuggingFaceTokenizer tokenizer, OrtSession session) {}

    public EmbeddingClient(@Value("${embedding.model-dir:}") String modelDir) {
        this.modelDir = modelDir;
    }

    // 과거 사례(문서): 접두어 없이
    public float[] embedDocument(String text) {
        return embed(text);
    }

    // 신규 문제(검색 쿼리): "query: " 접두어
    public float[] embedQuery(String text) {
        return embed(QUERY_PREFIX + text);
    }

    private float[] embed(String text) {
        Model m = model();
        Encoding encoding = m.tokenizer().encode(text);
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        try (OnnxTensor ids = OnnxTensor.createTensor(env, new long[][]{encoding.getIds()});
             OnnxTensor mask = OnnxTensor.createTensor(env, new long[][]{encoding.getAttentionMask()});
             OrtSession.Result result = m.session().run(Map.of("input_ids", ids, "attention_mask", mask))) {
            return ((float[][]) result.get(0).getValue())[0];
        } catch (OrtException e) {
            throw new IllegalStateException("임베딩 실행 실패", e);
        }
    }

    // 첫 호출 시 1회 로딩 (앱 기동과 분리). 실패하면 null로 남아 다음 호출 때 다시 시도한다
    private Model model() {
        if (model != null) return model;
        synchronized (this) {
            if (model == null) model = load();
            return model;
        }
    }

    private Model load() {
        if (modelDir.isBlank()) {
            throw new IllegalStateException("embedding.model-dir(EMBEDDING_MODEL_DIR)가 설정되지 않았습니다");
        }
        Path dir = Path.of(modelDir);
        long start = System.nanoTime();
        try {
            HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.builder()
                    .optTokenizerPath(dir.resolve("tokenizer.json"))
                    .optMaxLength(MAX_TOKENS)
                    .optTruncation(true)
                    .build();
            OrtSession session = OrtEnvironment.getEnvironment()
                    .createSession(dir.resolve("model.onnx").toString(), new OrtSession.SessionOptions());
            log.info("임베딩 모델 로딩 {}ms, dir={}", (System.nanoTime() - start) / 1_000_000, dir);
            return new Model(tokenizer, session);
        } catch (Exception e) {
            throw new IllegalStateException("임베딩 모델 로딩 실패. dir=" + dir, e);
        }
    }
}
