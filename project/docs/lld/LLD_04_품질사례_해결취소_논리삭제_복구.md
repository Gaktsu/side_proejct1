# LLD 04 — 품질사례 해결 취소·논리 삭제 및 복구

- 작성 기준: `LLD 4.txt` 및 2026-10-26 검토·후속 결정
- 상태: **핵심 기능 설계 합의 / 세부 실패 복구 정책 미정**
- 범위: 해결 취소(RESOLVED→OPENED), 논리 삭제, 삭제 복구, Qdrant 검색 제외·재허용 요청
- 범위 밖: 백업 인프라·전면 장애 복구(LLD 05), 상세 API 응답 코드, 운영자 감사 체계

> 표기: **[결정]** 합의된 정책, **[제안]** 구체 구현 방식, **[미정]** 아직 결정하지 않은 항목.

## 1. 목표 및 입력

- 해결 취소: `qualityCaseId`, 취소 요청자 및 필요 시 사유.
- 논리 삭제: `qualityCaseId`, 삭제 권한이 있는 요청자, 필요 시 삭제 사유.
- 삭제 복구: 삭제된 `qualityCaseId`, 복구 요청자.
- **[결정]** QualityCase 원본은 삭제 후에도 MySQL에 보존한다.
- **[결정]** Qdrant의 오래된 벡터는 즉시 물리 삭제하지 않고 `searchable=false`로 검색 대상에서 제외한다.

## 2. 사전 조건

### 공통
1. 대상 QualityCase가 존재하는가?
2. 요청자가 해당 기능을 수행할 권한이 있는가?
3. 논리 삭제 여부와 현재 업무 상태가 해당 상태 전이를 허용하는가?
4. 인덱싱 진행/대기 작업과 충돌하는지 확인한다. **[미정]** 충돌 시 대기·거부·무효화 중 선택.

### 해결 취소
- 삭제되지 않았고 현재 `RESOLVED`인 QualityCase를 대상으로 한다.
- `InspectionResult`를 반드시 조회하거나 인덱싱이 전부 `COMPLETED`일 것을 강제하는 정책은 **확정되지 않았다**.

### 논리 삭제
- **[결정]** `OPENED`와 `RESOLVED` 모두 논리 삭제 대상이 될 수 있다.
- 삭제된 사례의 고유 접수번호는 다른 신규 사례에서 재사용하지 않는 정책을 유지한다.

### 복구
- 현재 논리 삭제 상태인 QualityCase만 복구한다.
- **[결정]** 삭제 전 업무 상태(OPENED/RESOLVED)를 그대로 유지한다.

## 3. 정상 흐름

### A. 해결 취소 (RESOLVED → OPENED)
1. 클라이언트가 QualityCase ID로 해결 취소를 요청한다.
2. Spring에서 사례 존재·권한·현재 `RESOLVED` 상태 및 삭제 여부를 확인한다.
3. **[결정]** 하나의 MySQL 트랜잭션에서 `status=OPENED`로 변경하고 **Qdrant 검색 제외 필요 작업(PENDING)**을 함께 기록한다.
4. COMMIT 후 사용자에게 해결 취소 결과를 알린다.
5. 비동기 작업이 Qdrant payload를 `searchable=false`로 갱신하고 완료 상태를 기록한다.
6. Qdrant 갱신 전이라도 RAG 결과 사용 시 MySQL 원본 상태를 확인해 OPENED 사례를 제외한다.

### B. 품질사례 논리 삭제
1. 클라이언트가 삭제 요청을 한다.
2. Spring은 사례 존재·권한·삭제 여부를 확인한다.
3. 하나의 MySQL 트랜잭션에서 `QualityCase.deleted_at`을 기록하고 Qdrant 검색 제외 작업(PENDING)을 함께 기록한다.
4. COMMIT 후 삭제 처리를 응답한다. 기본 목록·조회·신규 RAG 근거로 노출되지 않도록 제한한다.
5. 비동기 작업에서 기존 벡터의 `searchable=false`를 적용한다. 이미 벡터가 없다면 상태를 확인하고 필요한 완료 처리만 수행한다.
6. 과거 Analysis, Candidate, InspectionResult 참조 이력은 자동 물리 삭제하지 않는다. **[미정]** 삭제된 원본에 대한 과거 기록 조회 권한과 표시 방식.

### C. 논리 삭제 사례 복구
1. 관리자/권한자가 삭제된 QualityCase ID로 복구를 요청한다.
2. Spring은 현재 논리 삭제 여부를 확인한다.
3. MySQL 트랜잭션에서 `deleted_at=NULL`로 복구하고, 검색 반영이 필요한 경우 해당 작업을 영속적으로 기록한다.
4. 삭제 전 `OPENED`였다면 `OPENED`로 유지하고 Qdrant는 검색 제외한다.
5. 삭제 전 `RESOLVED`였다면 **현재 원본과 Qdrant 벡터의 모델·버전·인덱싱 유효성**을 확인한다. 유효하면 검색 재허용 작업을, 유효하지 않으면 재인덱싱 작업을 수행한다.
6. 검증 및 비동기 반영이 완료된 후에만 해당 사례가 RAG 검색에 사용될 수 있다.

## 4. 데이터 변경

| 작업 | MySQL / QualityCase | MySQL / 검색 동기화 작업 | Qdrant |
|---|---|---|---|
| 해결 취소 | `RESOLVED → OPENED` | 검색 제외 PENDING → 완료/실패 | `searchable=false` |
| 논리 삭제 | `deleted_at=삭제 시각`, 업무 상태 유지 | 검색 제외 PENDING → 완료/실패 | `searchable=false` |
| OPENED 사례 복구 | `deleted_at=NULL`, OPENED 유지 | 필요 작업 기록/갱신 | 검색 제외 유지 |
| RESOLVED 사례 복구 | `deleted_at=NULL`, RESOLVED 유지 | 재허용 또는 재인덱싱 작업 | 검증 후 `searchable=true` 가능 |

- **[결정]** 별도 `QualityCase_Backup` 테이블에 옮기고 원본 테이블에서 삭제하는 방식은 채택하지 않는다. 논리 삭제와 정기 DB 백업은 서로 다른 목적이다.
- **[결정]** `QualityCase.status`에 `DELETED`를 섞기보다 `deleted_at`으로 삭제 여부를 분리한다.
- **[결정]** 인덱싱 완료 여부와 검색 가능 여부를 구분한다. 인덱싱 상태를 무조건 `DELETED`로 덮어쓰지 않는다.
- **[제안]** Qdrant의 `searchable`은 payload boolean으로 관리한다. 실제 필드·작업 테이블 구조는 LLD 01과 스키마 검토에서 확정한다.

## 5. 트랜잭션 경계

- **[결정]** MySQL을 Source of Truth로 유지한다. 업무 상태 또는 `deleted_at` 변경과 그에 따른 **검색 동기화 작업 기록**은 하나의 MySQL 트랜잭션으로 처리한다.
- MySQL COMMIT 후 별도 비동기 처리로 Qdrant payload 변경을 수행한다. 두 저장소를 하나의 MySQL 트랜잭션으로 묶을 수 없다.
- Qdrant 변경 성공 후 작업 완료를 MySQL에 기록한다. 기록 실패 시 재시도/멱등 처리가 필요하다(LLD 05).
- **[중요]** 트랜잭션이 성공해도 Qdrant 반영까지 즉시 완료됐다는 뜻은 아니다. 사용자 응답도 **업무상 변경 접수/완료**와 **검색 동기화 완료**를 구분한다.

## 6. 실패 흐름과 데이터 안전성

| 실패 지점 | 남는 상태 | 기본 대응 |
|---|---|---|
| MySQL 트랜잭션 실패 | 업무 상태 변경 및 작업 기록 모두 미커밋 | ROLLBACK, 요청 실패 응답 |
| MySQL 성공, Spring 종료 | 업무 변경 및 PENDING 작업 기록 유지 | 재시작 후 미완료 동기화 작업 탐색 |
| Qdrant `searchable=false` 갱신 실패 | MySQL에는 삭제/OPENED 반영, Qdrant는 이전 payload 가능 | 미완료 작업 재처리, **LLM 사용 전 MySQL 유효성 검증** |
| Qdrant 성공, MySQL 완료 기록 실패 | Qdrant에는 반영됐으나 작업 상태 미완료 | 현재 상태 확인 후 멱등 재시도 |
| 복구 중 인덱싱 버전 불일치 | 복구된 MySQL 원본과 과거 벡터가 불일치 | 검색 재허용 보류, 재인덱싱 필요로 기록 |

- **[결정]** 의도적으로 오래된 Qdrant 검색 결과를 사용자에게 노출해 오류를 발견하는 방식은 사용하지 않는다.
- **[결정]** Qdrant 필터만 신뢰하지 않고, 최종 후보 사용 전 MySQL에서 `status=RESOLVED`, `deleted_at=NULL`, 인덱싱 버전 유효성을 검사한다.
- **[미정]** 실패 작업 재시도 횟수·간격·오래된 PROCESSING 회수는 LLD 05에서 결정한다.

## 7. 최종 상태

| 작업 | 성공 조건 |
|---|---|
| 해결 취소 | MySQL OPENED 기록 + 검색 제외 작업 기록 완료. 최종 검색 사용은 차단됨 |
| 논리 삭제 | MySQL deleted_at 기록 + 검색 제외 작업 기록 완료. 일반 조회·RAG에서 제외됨 |
| OPENED 복구 | deleted_at=NULL, OPENED 유지, 검색 제외 유지 |
| RESOLVED 복구 | deleted_at=NULL, RESOLVED 유지, 검색 인덱스 검증 후 검색 재허용 |

> 사용자에게 업무 변경 성공을 안내한 후에도 Qdrant 동기화는 PENDING일 수 있다. 검색 반영 완료는 별도 상태로 관리한다.

## 8. 미정 사항 및 후속 검토

1. 삭제·취소의 상세 인가 정책과 감사 로그, 삭제 사유 필수 여부.
2. 삭제된 사례를 참조하는 기존 Analysis/InspectionResult의 조회·복구 화면 정책.
3. 인덱싱 PROCESSING 중 취소/삭제/복구가 들어왔을 때 작업 취소·버전 무효화·재실행 정책.
4. 검색 제외와 재허용 작업의 구체 엔티티 및 상태 모델(LLD 01/LLD 05).
5. Qdrant 작업 유실·장애·중복 처리의 상세 복구 정책(LLD 05).
6. 보존 기간, 별도 DB 백업 주기 등 운영 정책(향후 비기능 설계).

## 9. 기존 `LLD 4.txt` 대비 변경 기록

- **해결 취소 / 논리 삭제 / 삭제 복구** 세 흐름을 분리했다.
- 물리 삭제+백업 테이블 방식과 `status=DELETED`를 폐기하고 **`deleted_at` 논리 삭제**를 채택했다.
- 삭제된 OPENED 사례도 복구 대상이며, 복구 시 원래 업무 상태를 유지한다.
- MySQL 변경과 검색 제외 작업을 함께 기록하고 Qdrant는 비동기로 반영하도록 트랜잭션 경계를 명시했다.
- Qdrant 검색 제외 지연·실패 시에도 MySQL 최종 검증으로 부적절한 근거 사용을 차단한다.
