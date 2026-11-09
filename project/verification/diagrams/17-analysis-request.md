# 분석 요청 (접수)

`POST /api/quality-cases/{id}/analyses` — 접수만 하고 202. 검색은 커밋 뒤 분석 처리기(19)가 한다

```mermaid
flowchart TD
    A([분석 요청]) --> L[사례 행 잠금<br/>SELECT ... FOR UPDATE<br/>같은 사례 동시 요청은 여기서 줄 섬]
    L --> B{사례가 있고<br/>삭제 안 됨?}
    B -- 아니오 --> R404[/404 Not Found<br/>변경 없음/]
    B -- 예 --> C{status RESOLVED?}
    C -- 예 --> R409A[/409 Conflict<br/>OPEN만 분석 가능, 변경 없음/]
    C -- 아니오 --> D{이 사례의 분석이<br/>이미 있음?}
    D -- 예 --> E{PENDING 또는<br/>SEARCHING?}
    E -- 예 --> R409B[/409 Conflict<br/>이미 등록된 분석 요청입니다<br/>응답에 기존 analysisId, 변경 없음/]
    E -- 아니오 --> T
    D -- 아니오 --> T{쿼리 텍스트<br/>1024토큰 초과?}
    T -- 예 --> R400[/400 Bad Request<br/>롤백, 기존 분석 그대로/]
    T -. 토크나이저 로딩 실패 .-> R503[/503 Service Unavailable<br/>롤백, 변경 없음/]
    T -- 아니오 --> F{끝난 분석이 있었음?<br/>CANDIDATES_READY·NO_CANDIDATES·FAILED}
    F -- 예 --> DEL[기존 후보 DELETE<br/>기존 분석 DELETE<br/>갱신하지 않고 지움]
    F -- 아니오 --> INS
    DEL --> INS[analysis INSERT<br/>status PENDING]
    INS --> R202[/202 Accepted<br/>analysisId, status PENDING/]

    L -.- N1[잠금 때문에 뒤 요청은 앞 요청이 만든 분석을 보고 409<br/>target_quality_case_id UNIQUE는 안전망]

    classDef ok fill:#d4edda,stroke:#28a745,color:#155724
    classDef fail fill:#f8d7da,stroke:#dc3545,color:#000000
    classDef note fill:#eeeeee,stroke:#999999,color:#333333
    class R202 ok
    class R404,R409A,R409B,R400,R503 fail
    class N1 note
```

- 토큰 검사는 텍스트를 저장하지 않고 길이만 본다. 처리기는 실행 시점의 사례로 텍스트를 다시 만든다.
- 400·503·409는 모두 롤백이라 끝난 기존 분석과 후보는 그대로 남는다 (지우기는 모든 검사를 통과한 뒤).
