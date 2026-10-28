# LLD 01 — 품질사례 등록·해결·수정과 Qdrant 인덱싱

- 상태: **초안 / 1단계 검토 완료 (세부 복구 정책 미확정)**
- 기반 자료: `LLD 1.txt`, 신규 품질 사례 등록·품질 사례 해결·해결된 품질 사례 수정 흐름도, HLD/ADR-0001 및 이후 논의
- 범위: QualityCase의 등록, 해결 상태 전환, RESOLVED 사례 수정, MySQL↔Qdrant 인덱싱
- 범위 밖: 실제 RAG 질의 검색, JEV/Reranker, LLM 분석 실행·결과, 세부 API 스키마

> **기록 구분**: 아래의 **결정**은 대화에서 합의한 방향이며, **제안**은 구현 가능한 예시, **미정**은 확정되지 않은 설계이다. 실제 테이블·컬럼의 존재를 뜻하지 않는다.

## 1. 설계 원칙 및 주요 결정

1. **[결정]** MySQL의 QualityCase가 원본 데이터(Source of Truth)이다. Qdrant는 RESOLVED 사례의 검색용 인덱스이다.
2. **[결정]** 신규 품질 문제는 OPENED로 등록하며, 해결 이전에는 Qdrant에 반영하지 않는다. (실제 상태 상수명 OPENED/OPEN은 코드와 통일 필요.)
3. **[결정]** QualityCase를 RESOLVED로 변경하는 작업과 **인덱싱 필요 작업(PENDING)을 MySQL에 기록하는 작업**은 **같은 MySQL 트랜잭션**에서 커밋한다. Embedding 및 Qdrant 호출은 해당 트랜잭션 밖에서 처리한다.
4. **[결정]** 업무상 해결 완료와 검색 반영 완료는 별개이다. 사용자에게 해결 완료 및 검색 반영 대기 상태를 표시한다.
5. **[잠정 목표]** 정상적인 운영 조건에서 검색 반영은 **10분 이내**를 목표로 하되, 측정 기준·초과 상태 처리는 미정이다.
6. **[결정]** 동일 인덱싱 작업의 재실행을 허용하되, 중복·오래된 작업으로 인덱스가 잘못 갱신되지 않아야 한다.
7. **[잠정 결정]** 일시적 장애는 최대 **5회 재시도**를 검토한다. 모든 오류에 재시도하는 것은 아니며 간격·횟수의 산정 방식은 미정이다.
8. **[결정]** 삭제 요청은 원본의 즉시 물리 삭제가 아닌 **보존 가능한 삭제**를 우선 고려한다. 실제 검색에는 유효한 RESOLVED 사례만 제공해야 한다.

## 2. 입력과 사전 조건

### 신규 품질사례 등록
- 입력(현재 초안): 접수 식별자(기존 문서의 ‘오류 코드’), 공정 위치, 설비, 생산 LOT, 문제 현상, 생산 수량, 불량 수량, 발생 시각.
- 사전 조건: 필수 입력·형식 유효성, 공정·설비 존재 여부, 접수 식별자 중복 여부.
- **[검증 필요]** ‘오류 코드’가 오류 유형 코드인지 사례별 고유 접수번호인지 명시한다. 고유 접수번호라면 DB `UNIQUE`를 적용한다. 설비와 공정의 연관 관계도 검토한다.

### 품질사례 해결 / RESOLVED 사례 수정
- 대상 QualityCase 존재 여부, 사용자 쓰기 권한, 허용 상태 전이, 갱신할 필드의 유효성을 확인한다.
- 해결 사례의 임베딩 입력 후보(기존 문서): `lot_no`, `title`, `problem_description`, `defect_type`, `root_cause`, `corrective_action`, 선택적 `result_summary`.
- **[미정]** 최종 임베딩 입력 필드 및 수정 시 재임베딩을 유발하는 필드.

## 3. 정상 흐름

### A. 신규 등록 (OPENED)
1. 사용자가 품질사례 등록을 요청한다.
2. Spring이 입력과 권한, 관련 공정·설비 존재 및 접수번호 유효성을 검사한다.
3. MySQL의 하나의 트랜잭션으로 QualityCase를 **OPENED** 상태로 INSERT한다.
4. 성공 시 등록된 사례의 식별자 및 상태를 반환한다. **Qdrant는 변경하지 않는다.**

### B. OPENED → RESOLVED
1. 사용자 해결 완료 요청을 검증하고 대상 사례의 현재 상태를 확인한다.
2. **하나의 MySQL 트랜잭션**에서 QualityCase를 RESOLVED로 UPDATE하고 해당 버전의 인덱싱 작업을 영속 기록한다(`embedding_status=PENDING`, `qdrant_status=WAITING`).
3. COMMIT 이후 사용자에게 **‘해결 처리 완료 / RAG 검색 반영 대기’**를 표시한다.
4. 별도 비동기 처리기가 작업을 취득해 Embedding을 수행한다. 수행 중 `embedding_status=PROCESSING`으로 관리한다.
5. Embedding 완료 후 별도의 MySQL 트랜잭션에서 `embedding_status=COMPLETED`, `qdrant_status=PENDING`을 **함께 기록**한다. 벡터 영속 보존을 선택했다면 벡터 또는 저장 위치도 이때 함께 기록한다.
6. Qdrant 작업을 수행하고 성공하면 `qdrant_status=COMPLETED`를 기록한다. 이 시점부터 해당 사례가 검색 조건을 충족하는지 확인 가능하다.
7. 장애 발생 시 실패한 단계의 상태와 오류 정보를 남기고 재처리 정책에 따라 다시 시도한다.

### C. RESOLVED 사례 수정
1. 사용자 수정 요청을 검증한다.
2. **[잠정 정책]** 인덱싱 작업이 PENDING/PROCESSING이면 수정 요청을 제한한다. FAILED/오래된 PROCESSING에서 해제·복구하는 기준은 미정이다.
3. 수정 필드가 임베딩에 영향을 준다면 MySQL 트랜잭션에서 원본을 UPDATE하고 **새 버전의 인덱싱 작업(`embedding_status=PENDING`, `qdrant_status=WAITING`)**을 기록한다.
4. 비동기 처리기가 새 벡터를 생성한 뒤 `embedding_status=COMPLETED`와 `qdrant_status=PENDING`을 함께 기록하고, Qdrant 반영 성공 시 `qdrant_status=COMPLETED`로 갱신한다.
5. 수정 대상이 임베딩에 영향을 주지 않는다면 재인덱싱 필요 여부를 정책에 따라 판단한다.

## 4. 저장 데이터와 상태

| 저장소 | 신규 등록 | 해결 전환 | 해결 사례 수정 |
|---|---|---|---|
| MySQL / QualityCase | INSERT (OPENED) | UPDATE (RESOLVED, 원인·조치 등) | 변경 필드 UPDATE |
| MySQL / 인덱싱 작업 기록 | 기본적으로 작업 없음 | Embedding PENDING / Qdrant WAITING → 단계별 COMPLETED/FAILED | 새 버전 작업 기록 및 단계별 상태 갱신 |
| Qdrant | 변경 없음 | RESOLVED 사례의 벡터 UPSERT | 최신 유효 버전 벡터로 갱신 또는 검색 제외 |

- **[제안]** 인덱싱 기록은 `quality_case_id`, `source_version`, `embedding_status`, `qdrant_status`, `attempt_count`, `last_error`, `updated_at` 등의 개념을 관리할 수 있다. 실제 테이블·FK 구조는 미정이다.
### Embedding / Qdrant 단계별 상태 관리 [결정]

단일 인덱싱 상태 대신 **Embedding과 Qdrant의 작업 상태를 별도 필드로 관리한다.** 이는 MySQL의 B-Tree 인덱스 상태가 아니라 Qdrant 검색 인덱스 반영을 위한 처리 단계 상태다.

| 단계 | Embedding 상태 | Qdrant 상태 | 의미 |
|---|---|---|---|
| 인덱싱 필요 작업 기록 직후 | PENDING | WAITING | Embedding 처리 대기, Qdrant는 선행 결과 대기 |
| Embedding 실행 중 | PROCESSING | WAITING | 벡터 생성 중 |
| Embedding 완료·상태 커밋 | COMPLETED | PENDING | 벡터 생성 완료, Qdrant 저장 대기 |
| Qdrant 저장 중 | COMPLETED | PROCESSING | 벡터 반영 중 |
| Qdrant 저장 완료 | COMPLETED | COMPLETED | 해당 원본 버전의 인덱싱 작업 완료 |
| Embedding 실패 | FAILED | WAITING | Qdrant는 아직 실행할 수 없음 |
| Qdrant 실패 | COMPLETED | FAILED | 저장된 벡터를 재사용하거나 재임베딩하여 복구 검토 |

- `WAITING`은 이전 단계의 완료를 기다리는 상태로, `PENDING`(실행 가능한 대기 작업)과 구분한다.
- **[결정]** QualityCase의 해결 상태(RESOLVED)와 두 단계의 처리 상태는 서로 독립적이다.
- **[결정]** 해당 원본 버전의 인덱싱은 **두 상태가 모두 COMPLETED이고 최신 버전이 일치할 때만** 최종 완료로 본다.
- **[결정]** Embedding 완료 기록과 Qdrant PENDING 전환은 같은 MySQL 트랜잭션으로 수행한다. 중간에 Spring이 종료되어도 상태가 절반만 반영되지 않도록 한다.
- **[미정]** PROCESSING 장기 고착, FAILED 후 재시도 시 상태 전이, 작업 선점과 동시 실행 제어의 상세 규칙.

- **[미정]** 벡터를 MySQL/S3에 별도 보존할지, 원문에서 재임베딩할지, Qdrant 스냅샷을 활용할지 결정하지 않았다.

## 5. 트랜잭션 경계

**동일 MySQL 트랜잭션 안**
- 해결 전환: `QualityCase UPDATE` + `Embedding PENDING / Qdrant WAITING 기록`.
- 수정 시 재인덱싱 필요: `QualityCase UPDATE` + `새 버전 작업의 Embedding PENDING / Qdrant WAITING 기록`.

**MySQL 트랜잭션 밖**
- Embedding 호출과 Qdrant UPSERT는 MySQL 트랜잭션 밖에서 각각 실행한다. **Embedding 완료 기록 + Qdrant PENDING 전환**은 하나의 짧은 MySQL 트랜잭션으로 묶고, Qdrant 성공 시 또 다른 MySQL 트랜잭션에서 `qdrant_status=COMPLETED`를 기록한다.
- 외부 처리 도중 DB 트랜잭션·커넥션을 계속 점유하지 않는다.
- DB 실패 시 기존 트랜잭션을 종료(롤백)한 뒤, 오류 종류에 따라 **새 트랜잭션**으로 재시도한다.

## 6. 대표 실패 시나리오와 복구 방향

| 실패 지점 | 유지해야 할 사실 | 복구 방향 / 미정 |
|---|---|---|
| 신규 등록 DB INSERT 실패 | 등록 성공을 허위 응답하지 않음 | 롤백, 중복·커밋 여부 확인, 재시도 가능한 오류만 재시도 |
| RESOLVED 변경 트랜잭션 실패 | 상태 변경과 단계별 작업 상태 기록 둘 다 롤백 | 실패 응답; 작업 미등록 |
| 위 트랜잭션 COMMIT 직후 Spring 종료 | QualityCase 변경과 Embedding PENDING / Qdrant WAITING 기록은 남음 | 재시작 이후 미완료 작업을 찾아 실행할 주체 필요 **[미정]** |
| Embedding 호출 실패 | 원본 QualityCase와 작업 기록 유지; Embedding FAILED, Qdrant WAITING 가능 | 적절한 오류만 재임베딩 및 재시도 |
| Embedding 성공 후 Spring 종료 | 작업 기록 유지; 완료 상태 커밋 전이라면 Embedding 재실행 가능 | 벡터가 영속 보관되지 않았다면 재임베딩 필요 |
| Qdrant UPSERT 실패 | Embedding COMPLETED, Qdrant FAILED/PROCESSING 등으로 남을 수 있음 | 저장된 벡터로 재시도 또는 재임베딩 **[미정]** |
| Qdrant UPSERT 성공, MySQL 완료 기록 실패 | 실제 Qdrant 성공·DB Qdrant PROCESSING/PENDING 불일치 가능 | 고정 Point ID 기반 멱등 UPSERT 및 작업 상태 재확인; 최신 버전 검증 필요 |
| 과거 버전 작업이 뒤늦게 완료 | 최신 검색 인덱스를 훼손하면 안 됨 | 버전 비교·직렬화 등 구체 수단 **[미정]** |

> **주의**: 비동기 작업 실패는 이미 종료된 HTTP 요청에 `GlobalExceptionHandler`로 다시 실패 응답을 보낼 수 없다. 작업 상태를 남기고 상태 조회로 전달해야 한다.

## 7. 검색 노출 및 삭제 정책

- **[결정]** 정상 검색에는 **유효하고 해결 완료된(RESOLVED) 사례**만 사용한다.
- **[미정]** 오래된 벡터가 남아 있을 때 검색 결과 노출 방식과 최신 버전 검증 위치(Qdrant payload / MySQL 조회 등).
- 삭제·취소 시 데이터 훼손 감지 및 복구가 목표이므로 **검색 요청을 의도적으로 예외로 만들기보다는** 원본 보존(논리 삭제 후보), 검색 제외, 불일치 탐지 로그를 별도로 설계한다.
- 기존 AI 분석 이력을 보존하기로 한 정책과의 연관 관계·참조 무결성은 후속 상세 설계에서 결정한다.

## 8. 미정 사항과 결정이 필요한 질문

1. 미완료 인덱싱 작업을 재시작 시점 외에도 어떤 방식으로 찾아낼까? (작업 폴링, 이벤트+보완 검사 등)
2. 인덱싱 작업의 단계별 상태(`embedding_status`, `qdrant_status`)를 별도 테이블로 관리할까, QualityCase에 둘까?
3. 오류 유형별 재시도 한도·간격·작업 격리·최종 실패 상태는 어떻게 정할까?
4. 수정 중 인덱싱 제한 상태가 고착되면 어떻게 풀까?
5. 벡터를 반영구 보존할까? 그렇다면 모델 버전·저장 비용·삭제 시점은?
6. RESOLVED 철회·삭제·변경 중 기존 Qdrant 벡터를 언제 검색에서 제외할까?
7. 임베딩 모델 전환 시 재임베딩 완료와 검색 대상 전환을 어떤 기준으로 검증할까?
8. 정상 시 ‘10분 이내 검색 반영’ 목표를 어느 시점부터 측정하고 어떻게 검증할까?

## 9. 기존 다이어그램 수정 체크리스트

### 신규 품질 사례 등록.drawio.png
- `접수된 오류 코드`가 사례별 고유 접수번호임을 표시하고, 중복 조회 외에 DB UNIQUE 충돌 처리 경로 추가.
- `재시도 중인가?`가 클라이언트 중복 재요청인지 DB 실패의 내부 재시도인지 분리.
- Qdrant/임베딩 분기를 **두지 않고**, MySQL INSERT 커밋 이후 등록 완료 응답으로 종료.

### 품질 사례 해결.drawio.png
- MySQL 상태 RESOLVED 변경과 인덱싱 `PENDING` 기록을 **하나의 트랜잭션 범위**로 표시.
- `COMMIT → 해결 완료·인덱싱 대기 응답` 경로와 `COMMIT 이후 → 비동기 인덱싱` 경로를 분리.
- Embedding/Qdrant 실패 시 원래 HTTP 요청에 최종 실패를 반환하는 분기를 **작업 상태 FAILED/재시도**로 변경.
- 서버 재시작 후 미완료 작업을 찾는 경로와 Qdrant 완료 후 DB 상태 기록 실패 경로 추가.
- Embedding과 Qdrant의 상태를 별도 표시할 것. 최초 `Embedding=PENDING / Qdrant=WAITING`, 임베딩 완료 시 `Embedding=COMPLETED / Qdrant=PENDING`으로 전이시키는 경로 추가.

### 해결된 품질 사례 수정.drawio.png
- 수정 요청 시 기존 버전 Embedding/Qdrant의 PENDING/PROCESSING 검사를 표시하고, 충돌 시 별도 안내 흐름 추가.
- MySQL 수정 + **신규 버전 인덱싱 작업 기록(Embedding=PENDING, Qdrant=WAITING)**을 하나의 트랜잭션으로 표시.
- 새 임베딩이 실패할 때 **기존 Qdrant 벡터의 검색 노출 여부는 미정**으로 명시.
- 예전 인덱싱 작업이 늦게 완료되어 최신 벡터를 덮어쓰지 않도록 버전 검증 단계 표시.
- 해결 처리와 마찬가지로 사용자 응답과 비동기 완료 경로를 분리.

---

**다음 단계**: 이 문서를 1단계 LLD의 기준안으로 사용하고, 보류한 선택은 ADR 또는 후속 LLD에서 결정한다. 이번 문서는 상세 구현 및 장애 복구 완결을 선언하지 않는다.
