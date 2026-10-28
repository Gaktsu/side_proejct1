# QualityCase 수정

`PATCH /api/quality-cases/{id}`

```mermaid
flowchart TD
    A([수정 요청]) --> A1{id의 품질사례가 DB에 있음?<br/>행 잠금 SELECT ... FOR UPDATE}
    A1 -- 아니오 --> R404C[/404 Not Found<br/>사례 없음, 변경 없음/]
    A1 -- 예 --> B{불량 수량이<br/>생산 수량보다 큼?}
    B -- 예 --> R400Q[/400 Bad Request<br/>수량 모순, 변경 없음/]
    B -- 아니오 --> C{productId의 제품이<br/>DB에 있음?}
    C -- 아니오 --> R404P[/404 Not Found<br/>제품 없음, 변경 없음/]
    C -- 예 --> D{processId의 공정이<br/>DB에 있음?}
    D -- 아니오 --> R404R[/404 Not Found<br/>공정 없음, 변경 없음/]
    D -- 예 --> E{equipmentId를<br/>보냈음?}
    E -- 아니오 --> S
    E -- 예 --> F{equipmentId의 설비가<br/>DB에 있음?}
    F -- 아니오 --> R404E[/404 Not Found<br/>설비 없음, 변경 없음/]
    F -- 예 --> F2{설비가 선택한<br/>공정 소속임?}
    F2 -- 아니오 --> R400M[/400 Bad Request<br/>설비-공정 불일치, 변경 없음/]
    F2 -- 예 --> S{사례 상태?}
    S -- OPEN --> O1{원인·조치·결과 중<br/>하나라도 보냈음?}
    O1 -- 예 --> R400O[/400 Bad Request<br/>OPEN은 /resolve로 해결, 변경 없음/]
    O1 -- 아니오 --> G[등록 정보만 교체<br/>updatedAt 갱신]
    G --> R200[/200 OK<br/>이력·job 없음, 버전 그대로/]
    S -- RESOLVED --> H1{원인·조치가<br/>비어 있음?}
    H1 -- 예 --> R400H[/400 Bad Request<br/>원인·조치 필수, 변경 없음/]
    H1 -- 아니오 --> H2[직전 값 → quality_case_revision INSERT<br/>원인·조치·결과까지 교체, sourceVersion +1]
    H2 --> T{토크나이저 사용 가능 +<br/>문서 텍스트 1024토큰 이하?}
    T -- 토크나이저 불가 --> R503[/503 Service Unavailable<br/>롤백, 변경 없음/]
    T -- 초과 --> R400T[/400 Bad Request<br/>토큰 초과, 롤백/]
    T -- 예 --> H3[새 버전 indexing_job INSERT<br/>embedding PENDING / qdrant WAITING]
    H3 --> R200R[/200 OK<br/>이력 1행 + 새 버전 + 새 job 커밋/]

    classDef ok fill:#d4edda,stroke:#28a745,color:#155724
    classDef fail fill:#f8d7da,stroke:#dc3545,color:#000000
    class R200,R200R ok
    class R404C,R400Q,R404P,R404R,R404E,R400M,R400O,R400H,R503,R400T fail
```

- `equipmentId`를 null로 보내면 설비 연결이 해제된다. 사례번호·상태·시각은 바꿀 수 없다.
- 이전 버전 job이 처리 중이어도 막지 않는다. 처리기가 버전을 비교해 옛 job을 SUPERSEDED로 건너뛴다 (13-indexing-worker.md).
- 이전 버전으로 되돌리기는 이력 값을 그대로 PATCH한다 (새 버전이 됨, 이력은 지워지지 않음).
