# 분석 처리기 (쿼리 임베딩 → Qdrant → 후보 저장)

`AnalysisWorker` — 요청 없이 단일 스레드가 1초마다 `analysis`를 폴링 (인덱싱·검색 노출 처리기와 별도 스레드, 임베딩 계산은 10번 잠금으로 1건씩)

```mermaid
flowchart TD
    S([기동 완료]) --> S1[SEARCHING 분석을 모두 PENDING으로<br/>이전 프로세스가 처리하다 멈춘 것]
    S1 --> A([폴링])
    A --> B{PENDING 분석 있음?}
    B -- 아니오 --> W[/다음 주기까지 대기/]
    B -- 예 --> C{가장 오래된 분석을 선점<br/>PENDING → SEARCHING<br/>조건부 UPDATE 1행?}
    C -- 아니오 --> A
    C -- 예 --> D[대상 사례를 지금 상태로 읽어 쿼리 텍스트 생성<br/>삭제·해결됐어도 확인하지 않음, 텍스트는 로그만]
    D --> E{쿼리 임베딩 성공?}
    E -- 아니오 --> FL[/FAILED + error_message<br/>재시도 없음, 후보 없음/]
    E -- 예 --> Q{Qdrant top-10 검색 성공?<br/>searchable=true 필터}
    Q -- 아니오 --> FL
    Q -- 예 --> M[후보 id로 MySQL 사례 조회<br/>삭제된 사례도 읽음]
    M --> LOOP{후보마다:<br/>MySQL에 있음?}
    LOOP -- 아니오 --> SKIP[저장 안 함, 로그만<br/>FK 때문에]
    LOOP -- 예 --> V1{삭제됨?}
    V1 -- 예 --> RD[invalid_reason = DELETED]
    V1 -- 아니오 --> V2{RESOLVED 아님?}
    V2 -- 예 --> RN[invalid_reason = NOT_RESOLVED]
    V2 -- 아니오 --> V3{payload sourceVersion<br/>≠ 현재 버전?}
    V3 -- 예 --> RS[invalid_reason = STALE_VERSION]
    V3 -- 아니오 --> RV[invalid_reason = NULL<br/>근거로 사용 가능]
    RD --> SAVE
    RN --> SAVE
    RS --> SAVE
    RV --> SAVE
    SKIP --> SAVE
    SAVE{invalid_reason NULL인<br/>후보가 1개 이상?}
    SAVE -- 예 --> OK[/한 트랜잭션: 후보 전부 INSERT<br/>+ CANDIDATES_READY/]
    SAVE -- 아니오 --> NC[/한 트랜잭션: 후보 전부 INSERT<br/>+ NO_CANDIDATES 정상 결과/]

    D -.- N1[처리 중 다른 예외도 FAILED<br/>FAILED 기록 자체가 실패하면 SEARCHING으로 남아 기동 시 다시]

    classDef ok fill:#d4edda,stroke:#28a745,color:#155724
    classDef fail fill:#f8d7da,stroke:#dc3545,color:#000000
    classDef note fill:#eeeeee,stroke:#999999,color:#333333
    class OK,NC ok
    class FL fail
    class W,N1 note
```

- 사유는 삭제 → 미해결 → 버전 순서로 보고 처음 걸린 하나만 기록한다.
- 후보가 걸러지는 것은 실패가 아니다. `error_message`는 FAILED일 때만 채워진다.
- 완료 로그: 쿼리 텍스트, 후보 전체(순위, 사례 id, score, 사유, MySQL에 없는 id 포함), 임베딩·Qdrant·저장 소요 시간.
- Qdrant 호출은 연결 2초·응답 5초 타임아웃 (`QdrantClient` 상수). 타임아웃도 실패와 같아 FAILED.
