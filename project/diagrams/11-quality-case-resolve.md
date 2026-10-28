# QualityCase 해결 확정 (resolve + 인덱싱 job 기록)

`POST /api/quality-cases/{id}/resolve`

```mermaid
flowchart TD
    A([해결 요청]) --> B{id의 품질사례가<br/>DB에 있음?<br/>행 잠금 SELECT ... FOR UPDATE}
    B -- 아니오 --> R404[/404 Not Found<br/>사례 없음, 변경 없음/]
    B -- 예 --> C{상태가 RESOLVED임?<br/>동시 요청은 앞 요청 커밋 후 읽음}
    C -- 예 --> R409[/409 Conflict<br/>이미 해결됨, 변경 없음/]
    C -- 아니오 --> D[원인·조치·결과 저장<br/>status = RESOLVED, sourceVersion +1]
    D --> T{토크나이저를<br/>불러올 수 있음?}
    T -- 아니오 --> R503[/503 Service Unavailable<br/>토큰 검사 불가, 롤백되어 OPEN 유지/]
    T -- 예 --> T2{문서 텍스트가<br/>1024토큰을 넘음?}
    T2 -- 예 --> R400[/400 Bad Request<br/>토큰 초과, 롤백되어 OPEN 유지/]
    T2 -- 아니오 --> E[indexing_job INSERT<br/>embedding PENDING / qdrant WAITING]
    E --> R200[/200 OK<br/>RESOLVED + job 커밋, 검색 반영 대기/]
    R200 -.- N[임베딩·Qdrant 적재는 커밋 뒤<br/>인덱싱 처리기가 따로 실행<br/>13-indexing-worker.md]

    classDef ok fill:#d4edda,stroke:#28a745,color:#155724
    classDef fail fill:#f8d7da,stroke:#dc3545,color:#000000
    classDef note fill:#eeeeee,stroke:#999999,color:#333333
    class R200 ok
    class R404,R409,R503,R400 fail
    class N note
```

- 사례 변경과 job 기록이 한 트랜잭션이라 둘 다 커밋되거나 둘 다 롤백된다. 응답 `embeddingStatus=PENDING`, `qdrantStatus=WAITING`.
- 토큰 수만 동기로 센다(토크나이저만, 모델 추론 없음). 비동기로 미루면 해결됐는데 영영 검색되지 않는 사례가 생기기 때문.
- 첫 호출은 토크나이저 네이티브 라이브러리 로딩으로 약 5초 걸린다 (2026-10-27 측정, 이후 약 0.1초).
