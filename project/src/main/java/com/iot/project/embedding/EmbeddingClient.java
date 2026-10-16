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

    // 모델 최대는 6144지만 attention 비용이 토큰 수의 제곱으로 늘어 1024로 제한 (2026-10-16 측정:
    // 1133토큰 8초/+300MB, 2145토큰 21.6초/+1.2GB, 6144토큰은 15GB 노트북에서 OOM). 늘어난 메모리는 반환되지 않는다
    static final int MAX_TOKENS = 1024;
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
        if (encoding.getIds().length > MAX_TOKENS) {
            throw new EmbeddingInputTooLongException(encoding.getIds().length, MAX_TOKENS);
        }
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
                    // 자르지 않고 전체 토큰 수를 센다 (상한 검사는 embed에서). truncation을 켜면 DJL이
                    // tokenizer_config.json이 없다는 이유로 상한을 512로 바꿔 경고만 남기고 잘라 버린다
                    .optTruncation(false)
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
