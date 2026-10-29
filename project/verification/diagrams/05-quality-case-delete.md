# QualityCase 논리 삭제

`DELETE /api/quality-cases/{id}`

```mermaid
flowchart TD
    A([삭제 요청]) --> B{id의 품질사례가<br/>DB에 있고 삭제 안 됨?<br/>행 잠금 SELECT ... FOR UPDATE}
    B -- 아니오 --> R404[/404 Not Found<br/>없거나 이미 삭제됨, 변경 없음/]
    B -- 예 --> C[한 트랜잭션<br/>deleted_at = 지금, 상태·버전 그대로<br/>search_visibility_job PENDING 추가]
    C --> R204[/204 No Content<br/>행은 남음, restore 외 API에서 404/]
    R204 -.-> N[커밋 뒤 16번 처리기가<br/>Qdrant searchable=false<br/>반영 전 검색은 MySQL 대조가 거름]

    classDef ok fill:#d4edda,stroke:#28a745,color:#155724
    classDef fail fill:#f8d7da,stroke:#dc3545,color:#000000
    classDef note fill:#eeeeee,stroke:#999999,color:#333333
    class R204 ok
    class R404 fail
    class N note
```

- OPEN·RESOLVED 모두 삭제할 수 있다. 행이 남으므로 검사결과·분석이 참조해도 409가 나지 않는다 (예전 물리 삭제의 FK 409, RESOLVED 409는 없어짐).
- OPEN이어도 노출 job을 남긴다: 해결 취소 전 RESOLVED 시절 포인트가 Qdrant에 남아 있을 수 있다.
- 버전을 올리지 않으므로 진행 중인 인덱싱 job은 그대로 upsert(false)까지 간다 → 복구 때 그 벡터를 다시 쓴다.
