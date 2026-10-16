# QualityCase 해결 확정 (resolve + Qdrant 적재)

`POST /api/quality-cases/{id}/resolve`

```mermaid
flowchart TD
    A([해결 요청]) --> B{id의 품질사례가<br/>DB에 있음?<br/>행 잠금 SELECT ... FOR UPDATE}
    B -- 아니오 --> R404[/404 Not Found<br/>사례 없음, 변경 없음/]
    B -- 예 --> C{상태가 RESOLVED임?<br/>동시 요청은 앞 요청 커밋 후 읽음}
    C -- 예 --> R409[/409 Conflict<br/>이미 해결됨, 변경·적재 없음/]
    C -- 아니오 --> D[원인·조치·결과 저장<br/>status = RESOLVED, updatedAt 갱신<br/>UPDATE 즉시 실행 flush]
    D --> T{문서 텍스트가<br/>1024토큰을 넘음?}
    T -- 예 --> R400[/400 Bad Request<br/>토큰 초과, 롤백되어 OPEN 유지, 벡터 없음/]
    T -- 아니오 --> E{문서 텍스트 임베딩 +<br/>Qdrant upsert 성공?}
    E -- 아니오 --> R503[/503 Service Unavailable<br/>롤백되어 OPEN 유지, 벡터 없음/]
    E -- 예 --> F{MySQL 커밋 성공?}
    F -- 예 --> R200[/200 OK<br/>RESOLVED 저장, Qdrant에 벡터 적재/]
    F -- 아니오 --> R500[/500<br/>MySQL은 롤백되어 OPEN<br/>Qdrant에는 벡터가 남음/]

    classDef ok fill:#d4edda,stroke:#28a745,color:#155724
    classDef fail fill:#f8d7da,stroke:#dc3545,color:#000000
    classDef bug fill:#fff3cd,stroke:#ffc107,color:#856404
    class R200 ok
    class R404,R409,R400,R503 fail
    class R500 bug
```

- 노란 칸: upsert 뒤 커밋이 실패하는 경우(MySQL 연결 끊김, 앱 종료 등)는 코드에서 되돌릴 수 없다. Qdrant에 남은 벡터는 유사 사례 검색 API에서 MySQL `RESOLVED` 필터로 거르고, 담당자가 다시 resolve하면 같은 Point ID에 덮어써진다.
- 임베딩은 트랜잭션 안에서 실행되므로 그동안 DB 커넥션과 행 잠금을 쥔다. 같은 사례의 다음 요청이 `innodb_lock_wait_timeout`(기본 50초)보다 오래 기다리면 잠금 대기 실패로 500이 날 수 있다 (첫 호출 모델 로딩이 약 7초라 현재는 해당 없음).
- 토큰 초과는 다시 시도해도 같은 결과라 503이 아니라 400으로 응답한다. 토큰 수는 모델 추론 전에 토크나이저로 세므로 추론 비용은 들지 않는다 (첫 호출이면 모델 로딩은 일어난다).
