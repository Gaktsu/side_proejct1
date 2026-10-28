# 기존 코드와 LLD 1~4 차이 분석

기준: 사용자가 업로드한 `side_proejct1-main.zip` 내 `project/` 소스. **정적 코드 검토**이며 MySQL/Qdrant/ONNX 모델을 구동하거나 테스트를 실행하지 않음.

## 요약

- 유지: Product, ManufacturingProcess, Equipment CRUD; QualityCase 등록·조회·OPEN 수정의 유효성 검사; 검사·AI분석 엔티티 초안; `EmbeddingClient`, `QdrantClient`, `QualityCaseDocument`; 핵심 테스트 기반.
- 가장 큰 수정: `QualityCaseService.resolve()`가 현재 `@Transactional` 안에서 임베딩과 Qdrant upsert를 호출. LLD 1은 MySQL `RESOLVED` 변경과 `IndexingJob`을 같은 트랜잭션으로 저장하고 외부 처리는 비동기로 수행.
- 삭제: `QualityCaseService.delete()`는 `qualityCaseRepository.delete()`로 OPEN만 물리 삭제. LLD 4는 OPENED/RESOLVED 논리 삭제(`deleted_at`)와 검색 제외, 복구.
- 분석: Analysis, AnalysisCandidate, InspectionResult는 엔티티만 존재하고 기능 서비스/API는 없는 상태. JEV/LLM/SSE 역시 미구현.
- 주의: ZIP에는 `src/main/resources`, Flyway SQL, `application.properties`, `compose.yaml`, DB 모델 파일이 포함되지 않음. 실제 DB 스키마와 환경 구성을 검증할 수 없음. `CLAUDE.md`에 Flyway V1 사용 기록은 존재하지만 그 SQL 내용은 확인 불가.

## LLD별 검토

### LLD 1 — 등록·해결·수정·인덱싱
- **유지** `QualityCaseService.create/update` : 등록과 OPEN 사례 수정, 제품·공정·설비 정합성 검증, 수량 검사.
- **변경** `QualityCaseService.resolve` : 현재 MySQL 락 `findByIdForUpdate` → 상태 RESOLVED → flush → 같은 트랜잭션에서 ONNX 임베딩 및 Qdrant UPSERT → 실패 시 전체 롤백. `IndexingJob` 테이블/엔티티·Repository·worker 필요.
- **변경** RESOLVED 수정 정책: 현재 `update`는 `ResolvedQualityCaseUpdateException`을 던짐. LLD 1에서 RESOLVED 수정이 요구된다면 명시적인 수정 정책과 버전 증가·재인덱싱 추가 필요.
- **변경** `CaseStatus`: 코드에는 `OPEN`, 최신 설계에는 `OPENED`. 이미 저장된 데이터가 있으면 Enum만 바꾸면 안 되므로 기존 데이터 마이그레이션 필요.
- **추가** `index_version` 등 원본-벡터 일치 검사 수단, 비동기 job 상태 `embedding_status`/`qdrant_status`, 독립 재시도·복구.
- **추가** IndexingJob 저장과 QualityCase 변경은 MySQL 단일 트랜잭션. ONNX/Qdrant 외부 호출은 트랜잭션 밖.

### LLD 2 — 유사사례 검색·AI 분석
- **유지** `EmbeddingClient` : 로컬 ONNX PIXIE-Rune 임베딩, `embedQuery` / `embedDocument`; `QualityCaseDocument` : 검색/문서 텍스트 조립.
- **유지/변경** `QdrantClient.search` 및 `QualityCaseService.findSimilar` : 현재 Top-10 → MySQL RESOLVED 필터 → 검색 순서 보존. 향후 검색 제외 필터(`searchable=false`), 논리 삭제, 버전 유효성 검증 필요.
- **변경** `QdrantClient.upsert`의 `Point(id, vector)` 구조: 검색 가능 상태·원본 버전 등 payload 미지원. `search` 요청에도 payload 필터 없음.
- **추가** Analysis 비동기 실행, 요청·상태 조회, AnalysisCandidate 전체 후보 저장, 후보 평가(JEV/Reranker), 후보 조기 표시 SSE, LLM, 실패/재시도 정책.
- **추가** candidate 엔티티에 평가 성공·실패·미평가 상태, 채택 여부, 버전 등의 값. 지금은 `isNull`/`jevScore`만 있음.

### LLD 3 — 분석 결과와 최종 판단
- **유지** `QualityCase.rootCause`/`correctiveAction`/`resultSummary` 및 `resolve()` 입력 구조, `Analysis`의 JSON 결과 필드.
- **추가** `Analysis`의 서비스·리포지토리·API, 처리 상태/단계/오류 사유/완료 시각, 결과 저장 트랜잭션, 저장 실패 복구.
- **추가** `InspectionResult`의 저장·수정 및 비즈니스 검증 (`numericValue` 또는 `textValue` 필수) 서비스/API는 ZIP에서 없음.

### LLD 4 — 취소·논리 삭제·복구
- **변경** `QualityCaseService.delete` : 현재 OPEN만 물리 DELETE + FK 위반 처리; 논리 삭제는 상태를 유지하며 `deleted_at` 기록, 과거 분석/검사 참조 유지.
- **추가** RESOLVED → OPENED 해결 취소와 복구 API/서비스 및 삭제된 행의 수정 금지·일반 조회 제외.
- **추가** Qdrant payload `searchable=false` 작업을 MySQL 상태 변경 후 비동기로 등록. Qdrant 반영 전에도 MySQL 기준으로 사용자·LLM에 삭제·OPENED 사례가 전달되지 않아야 함.
- **정책** 삭제 전 상태 복원: OPENED는 계속 검색 제외, RESOLVED는 최신 유효 인덱스를 확인한 후 검색 재활성화.

## 영향받는 주요 파일

| 파일 | 변경 목적 |
|---|---|
| `QualityCase/QualityCase.java`, `CaseStatus.java` | deletedAt, indexVersion, OPEN/OPENED 결정, 상태 전이 |
| `QualityCase/QualityCaseService.java` | resolve 트랜잭션 분리·인덱싱 job, 논리 삭제·복구·취소, 검색 검증 |
| `QualityCase/QualityCaseRepository.java` | 비삭제 조회, 동시 수정 및 복구용 쿼리 |
| `QualityCase/QualityCaseController.java` | 취소·복구, 비동기 응답 계약 |
| `QualityCase/QualityCaseResponse.java` | 신규 데이터 노출 정책 |
| `qdrant/QdrantClient.java` | payload 저장/변경, 검색 필터, 버전 검색 검증 |
| `Analysis/Analysis.java`, `AnalysisCandidate/AnalysisCandidate.java` | 상태/평가 메타데이터 추가 |
| 신규 `IndexingJob` 관련 클래스 | 작업 영속화 및 worker 구현 |
| 신규 `Analysis`/`InspectionResult` 서비스·Controller | LLD 2~3 API |
| 관련 통합 테스트 | 변경된 성공·실패 의미에 맞게 재작성 |

## 구현 순서 제안

1. **먼저 운영 중 DB 스키마 확인**: `SHOW CREATE TABLE quality_case;`, `analysis`, `analysis_candidate`, Flyway `flyway_schema_history`. ZIP에 SQL이 없으므로 기존 `V1` 추측 금지.
2. 새 Flyway 버전으로 스키마 변경·데이터 이전 설계(특히 `OPEN`→`OPENED`), JPA 엔티티 대응. 이미 적용된 V1 수정 금지.
3. `IndexingJob` + 트랜잭션 분리 + 인덱싱 worker 및 실패·재시작 테스트. 기존 `resolve()` 실패시 롤백 테스트는 요구사항 변경에 맞춰 수정.
4. 논리 삭제·취소·복구 및 Qdrant `searchable` payload, 검색 응답의 MySQL 유효성 검증.
5. 검사 결과 API, Analysis 비동기 lifecycle/API, 후보 영속화, JEV/Reranker, SSE, LLM 순서로 기능 확장.
6. 3초 검색/15초 LLM 목표에 맞춘 성능 측정, 이후 LLD 5~6 단계 보완.

## 주의 사항

- 외부 시스템(Qdrant)과 MySQL을 동일한 로컬 DB 트랜잭션으로 롤백할 수 없음. 이전 `resolve()`의 `flush()`는 MySQL 커밋을 의미하지 않음.
- 저장된 Qdrant 포인트에 원본 `indexVersion` 정보가 없다면 버전 검사를 도입하면서 기존 벡터를 재인덱싱하거나 명확히 비검색 상태로 격리해야 함.
- `QdrantClient.delete()`는 존재하나 검색 제외 payload 방식으로 전환하면 정상 논리 삭제 흐름에서 직접 물리 DELETE 사용하지 않음.
- `CLAUDE.md`에는 'MVP에서 비동기 처리 제외' 등 과거 결정이 포함되어 최신 LLD와 충돌함. 코드 생성 AI에게 최신 합의가 우선임을 분명히 알리고 이 파일도 갱신해야 함.
- 분석/인덱싱의 정확한 상태 enum, 작업 복구 lease, 동시 갱신 버전 정책은 추후 구현 세부 설계로 명시·테스트할 것.
