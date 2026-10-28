# 인덱싱 처리기 (resolve·RESOLVED 수정 뒤 검색 반영)

`IndexingWorker` — 요청 없이 단일 스레드가 5초마다 `indexing_job`을 폴링

```mermaid
flowchart TD
    S([기동 완료]) --> S1[PROCESSING인 단계를 모두 PENDING으로<br/>이전 프로세스가 처리하다 멈춘 것]
    S1 --> A([폴링])
    A --> B{embedding 또는 qdrant가<br/>PENDING인 job 있음?}
    B -- 아니오 --> W[/다음 주기까지 대기/]
    B -- 예 --> C{가장 오래된 job을 선점<br/>PENDING → PROCESSING<br/>조건부 UPDATE 1행?}
    C -- 아니오 --> A
    C -- 예 --> V{job 버전 =<br/>사례 sourceVersion?}
    V -- 아니오 --> SP1[/진행 중이던 단계 = SUPERSEDED<br/>사례가 그사이 수정됨, 새 버전 job이 처리/]
    V -- 예 --> E[문서 텍스트 임베딩<br/>트랜잭션 밖]
    E --> E2{임베딩 단계에서<br/>시작했음?}
    E2 -- 예 --> E3[embedding COMPLETED + qdrant PENDING<br/>한 트랜잭션, 이어서 qdrant PROCESSING]
    E2 -- 아니오: Qdrant 단계 재시작 --> V2
    E3 --> V2{upsert 직전<br/>버전 다시 같음?}
    V2 -- 아니오 --> SP2[/qdrant = SUPERSEDED/]
    V2 -- 예 --> U[Qdrant upsert<br/>payload sourceVersion, searchable=true]
    U --> OK[/qdrant COMPLETED<br/>검색 반영 완료/]

    E -. 예외 .-> X{재시도 남음?<br/>최초 포함 5회}
    U -. 예외 .-> X
    X -- 예 --> R[PROCESSING 유지, 스레드는 놓아줌<br/>30초·1분·2분·4분 뒤 같은 스레드에서 재시도]
    R --> V
    X -- 아니오 --> F[/실패한 단계 = FAILED + last_error<br/>사례는 RESOLVED 그대로/]

    classDef ok fill:#d4edda,stroke:#28a745,color:#155724
    classDef fail fill:#f8d7da,stroke:#dc3545,color:#000000
    classDef note fill:#eeeeee,stroke:#999999,color:#333333
    class OK ok
    class F fail
    class SP1,SP2,W note
```

- 재시도는 현재 상태를 다시 읽어 어느 단계에서 이어갈지 정한다. Qdrant 단계에서 다시 시작하면 벡터를 보존하지 않으므로 다시 임베딩한다.
- 토큰 초과(`EmbeddingInputTooLongException`)는 재시도하지 않고 바로 FAILED (해결·수정 때 동기 검사하므로 사실상 발생하지 않음).
- 재시도 횟수는 메모리에서 센다. 재시작하면 PENDING으로 돌아가 처음부터 다시 센다 (프로세스를 죽이는 job이 반복되면 `attempt_count` 도입 검토).
- 단일 스레드·단일 인스턴스 전제. 여러 대가 되면 기동 시 복구가 다른 인스턴스의 job까지 되돌린다.
