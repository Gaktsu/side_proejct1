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
    // 토크나이저와 모델을 따로 로딩한다: 토큰 수 검사는 토크나이저만 있으면 되므로 모델(약 7초, 1.7GB)을 기다리지 않는다
    private volatile HuggingFaceTokenizer tokenizer;
    private volatile OrtSession session;

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

    // 문서 텍스트가 토큰 상한 안인지만 확인한다 (해결·수정 트랜잭션 안에서 동기로 호출)
    public void checkDocumentLength(String text) {
        encode(text);
    }

    private Encoding encode(String text) {
        Encoding encoding = tokenizer().encode(text);
        if (encoding.getIds().length > MAX_TOKENS) {
            throw new EmbeddingInputTooLongException(encoding.getIds().length, MAX_TOKENS);
        }
        return encoding;
    }

    private float[] embed(String text) {
        Encoding encoding = encode(text);
        OrtSession s = session();
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        try (OnnxTensor ids = OnnxTensor.createTensor(env, new long[][]{encoding.getIds()});
             OnnxTensor mask = OnnxTensor.createTensor(env, new long[][]{encoding.getAttentionMask()});
             OrtSession.Result result = s.run(Map.of("input_ids", ids, "attention_mask", mask))) {
            return ((float[][]) result.get(0).getValue())[0];
        } catch (OrtException e) {
            throw new IllegalStateException("임베딩 실행 실패", e);
        }
    }

    // 첫 호출 시 1회 로딩 (앱 기동과 분리). 실패하면 null로 남아 다음 호출 때 다시 시도한다
    private HuggingFaceTokenizer tokenizer() {
        if (tokenizer != null) return tokenizer;
        synchronized (this) {
            if (tokenizer == null) {
                Path path = modelDir().resolve("tokenizer.json");
                try {
                    tokenizer = HuggingFaceTokenizer.builder()
                            .optTokenizerPath(path)
                            // 자르지 않고 전체 토큰 수를 센다 (상한 검사는 encode에서). truncation을 켜면 DJL이
                            // tokenizer_config.json이 없다는 이유로 상한을 512로 바꿔 경고만 남기고 잘라 버린다
                            .optTruncation(false)
                            .build();
                } catch (Exception e) {
                    throw new IllegalStateException("임베딩 모델 로딩 실패(토크나이저). path=" + path, e);
                }
            }
            return tokenizer;
        }
    }

    private OrtSession session() {
        if (session != null) return session;
        synchronized (this) {
            if (session == null) {
                Path path = modelDir().resolve("model.onnx");
                long start = System.nanoTime();
                try {
                    session = OrtEnvironment.getEnvironment().createSession(path.toString(), new OrtSession.SessionOptions());
                } catch (Exception e) {
                    throw new IllegalStateException("임베딩 모델 로딩 실패. path=" + path, e);
                }
                log.info("임베딩 모델 로딩 {}ms, path={}", (System.nanoTime() - start) / 1_000_000, path);
            }
            return session;
        }
    }

    private Path modelDir() {
        if (modelDir.isBlank()) {
            throw new IllegalStateException("embedding.model-dir(EMBEDDING_MODEL_DIR)가 설정되지 않았습니다");
        }
        return Path.of(modelDir);
    }
}
