# 분석 조회

`GET /api/analyses/{analysisId}` — 상태와 후보 목록 (SSE 없이 이 조회로 확인)

```mermaid
flowchart TD
    A([조회 요청]) --> B{분석이 있음?}
    B -- 아니오 --> R404A[/404 Not Found/]
    B -- 예 --> C{대상 사례가<br/>삭제됨?}
    C -- 예 --> R404B[/404 Not Found<br/>복구하면 다시 200/]
    C -- 아니오 --> D[후보를 Qdrant 순위대로 읽음<br/>후보 사례까지 한 번에]
    D --> R200[/200 OK<br/>status, errorMessage, 후보 목록<br/>후보: 순위, 사례 id·caseCode·제목, vectorScore,<br/>invalidReason, jevScore·isNull은 null/]

    classDef ok fill:#d4edda,stroke:#28a745,color:#155724
    classDef fail fill:#f8d7da,stroke:#dc3545,color:#000000
    class R200 ok
    class R404A,R404B fail
```

- 후보 사례가 나중에 삭제·해결 취소돼도 후보는 그대로 보인다 (논리 삭제라 FK 문제 없음). `invalidReason`은 분석 당시의 판단이다. 삭제된 후보의 `caseCode`·제목도 그대로 나온다 (LLD 4 404 규칙의 예외, 2026-11-02 합의).
