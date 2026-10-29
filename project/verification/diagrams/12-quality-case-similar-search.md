# QualityCase 유사 사례 검색 (디버그·성능 측정용)

`GET /api/quality-cases/{id}/similar-cases`

```mermaid
flowchart TD
    A([검색 요청]) --> B{id의 품질사례가<br/>DB에 있고 삭제 안 됨?}
    B -- 아니오 --> R404[/404 Not Found<br/>사례 없음/]
    B -- 예 --> C{상태가 RESOLVED임?}
    C -- 예 --> R409[/409 Conflict<br/>OPEN 사례만 검색 가능/]
    C -- 아니오 --> T{쿼리 텍스트가<br/>1024토큰을 넘음?}
    T -- 예 --> R400[/400 Bad Request<br/>토큰 초과, Qdrant 호출 안 함/]
    T -- 아니오 --> E{쿼리 텍스트 임베딩 +<br/>Qdrant top-10 검색 성공?<br/>searchable=true 필터}
    E -- 아니오 --> R503[/503 Service Unavailable<br/>검색 실패/]
    E -- 예 --> F[후보 id로 MySQL 조회<br/>RESOLVED·삭제 안 됨·payload 버전 = 사례 버전인 것만<br/>Qdrant 순서 유지]
    F --> R200[/200 OK<br/>score + 사례 목록, 0~10건/]

    F -.- N[걸러지는 후보<br/>수정 뒤 아직 새 버전이 반영 안 된 옛 벡터<br/>해결 취소·삭제 뒤 searchable=false 반영 전 벡터<br/>OPEN 사례·MySQL에 없는 id<br/>payload 없는 LLD 1 이전 벡터는 Qdrant 필터에서 제외]

    classDef ok fill:#d4edda,stroke:#28a745,color:#155724
    classDef fail fill:#f8d7da,stroke:#dc3545,color:#000000
    classDef note fill:#eeeeee,stroke:#999999,color:#333333
    class R200 ok
    class R404,R409,R400,R503 fail
    class N note
```

- 분석 흐름은 Analysis API(LLD 2)가 맡고, 이 API는 검색 단계만 확인하는 용도다.
- 조회만 하므로 어느 분기에서도 DB 변경은 없다.
- 메서드 전체가 `readOnly` 트랜잭션이라 임베딩 동안 DB 커넥션 1개를 쥔다 (행 잠금은 없음). 첫 호출은 모델 로딩으로 약 7초.
- 걸러진 만큼 10건보다 적게 반환하고 다시 채우지 않는다.
