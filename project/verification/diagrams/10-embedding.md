# 임베딩 (첫 호출 시 모델 로딩, 동시 계산 1건)

`EmbeddingClient.embedDocument / embedQuery` (API 아님) — 호출처: 인덱싱 처리기(13, 문서), 분석 처리기(19, 쿼리), `/similar-cases`(12, 쿼리). 토큰 수 검사만 하는 `checkDocumentLength`·`checkQueryLength`는 T~LEN까지만 실행

```mermaid
flowchart TD
    A([embedDocument / embedQuery 호출]) --> Q{embedQuery?}
    Q -- 예 --> P[앞에 'query: ' 붙임]
    Q -- 아니오 --> T
    P --> T{토크나이저가<br/>이미 로딩됨?}
    T -- 아니오 --> S{embedding.model-dir<br/>설정되고 tokenizer.json<br/>로딩 성공?}
    S -- 아니오 --> RLOAD[/해당 호출만 실패<br/>IllegalStateException<br/>다음 호출 때 다시 시도/]
    S -- 예 --> ENC
    T -- 예 --> ENC[토큰화<br/>자르지 않고 전체 토큰 수를 셈]
    ENC --> LEN{1024토큰 초과?}
    LEN -- 예 --> RLONG[/EmbeddingInputTooLongException<br/>호출처에서 400 또는 FAILED/]
    LEN -- 아니오 --> M{모델이<br/>이미 로딩됨?}
    M -- 아니오 --> ML{model.onnx<br/>로딩 성공?}
    ML -- 아니오 --> RLOAD
    ML -- 예 --> KEEP[모델을 메모리에 유지<br/>약 7초, RSS +1.7GB]
    KEEP --> LOCK
    M -- 예 --> LOCK[임베딩 잠금 획득<br/>다른 스레드가 계산 중이면 끝날 때까지 대기]
    LOCK --> RUN[ONNX 실행<br/>출력 = CLS + 정규화된 벡터]
    RUN --> ROK[/float 1024 반환, 잠금 해제/]

    A -.- N1[앱 기동 시 모델을 읽지 않음<br/>모델 파일이 없어도 다른 API는 정상]
    LOCK -.- N2[모델·세션은 하나, 계산은 한 번에 1건<br/>동시 계산은 ONNX 메모리 수위를 올리고 반환 안 됨]

    classDef ok fill:#d4edda,stroke:#28a745,color:#155724
    classDef fail fill:#f8d7da,stroke:#dc3545,color:#000000
    classDef note fill:#eeeeee,stroke:#999999,color:#333333
    class ROK ok
    class RLOAD,RLONG fail
    class N1,N2 note
```
