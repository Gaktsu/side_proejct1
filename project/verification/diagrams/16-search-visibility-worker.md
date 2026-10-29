# 검색 노출 처리기 (Qdrant searchable 반영)

`SearchVisibilityWorker` — 요청 없이 단일 스레드가 1초마다 `search_visibility_job`을 폴링 (인덱싱 처리기와 별도 스레드)

```mermaid
flowchart TD
    S([기동 완료]) --> S1[PROCESSING job을 모두 PENDING으로<br/>이전 프로세스가 처리하다 멈춘 것]
    S1 --> A([폴링])
    A --> B{PENDING인 job 있음?}
    B -- 아니오 --> W[/다음 주기까지 대기/]
    B -- 예 --> C{가장 오래된 job을 선점<br/>PENDING → PROCESSING<br/>조건부 UPDATE 1행?}
    C -- 아니오 --> A
    C -- 예 --> D[사례를 MySQL에서 읽음<br/>삭제된 사례도 읽음]
    D --> E{status RESOLVED이고<br/>삭제 안 됨?}
    E -- 예 --> T[Qdrant set_payload searchable=true<br/>필터: id AND sourceVersion = 현재 버전]
    E -- 아니오 --> F[Qdrant set_payload searchable=false<br/>필터: id]
    T --> OK[/job COMPLETED<br/>조건에 맞는 포인트가 없으면 0건 갱신도 완료/]
    F --> OK

    T -. 예외 .-> X{재시도 남음?<br/>최초 포함 5회}
    F -. 예외 .-> X
    X -- 예 --> R[PROCESSING 유지, 스레드는 놓아줌<br/>30초·1분·2분·4분 뒤 같은 스레드에서 재시도]
    R --> D
    X -- 아니오 --> FL[/job FAILED + last_error<br/>검색은 MySQL 대조로 계속 정확/]

    classDef ok fill:#d4edda,stroke:#28a745,color:#155724
    classDef fail fill:#f8d7da,stroke:#dc3545,color:#000000
    classDef note fill:#eeeeee,stroke:#999999,color:#333333
    class OK ok
    class FL fail
    class W note
```

- job을 만드는 곳: 해결 취소(14), 논리 삭제(05), 복구(15), 인덱싱 완료(13).
- job에는 목표 값이 없다. 실행 시점의 MySQL 상태로 정하므로 같은 사례의 job이 여러 개여도, 순서가 섞여도 마지막 실행이 최신 상태로 맞춘다.
- `searchable`을 바꾸는 곳은 이 처리기 하나뿐이다 (인덱싱 처리기의 upsert는 항상 false).
- 벡터 버전이 현재 버전과 다르면 true가 되지 않는다 → 옛 벡터를 다시 검색에 쓰지 않는다. 현재 버전 인덱싱이 끝나면 13번이 job을 다시 넣는다.
