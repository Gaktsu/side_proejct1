# LLD 2 분석 요청·유사 사례 후보 저장 요구사항 점검 (구현 후)

- 점검: 2026-10-31, 브랜치 `feature/lld2-analysis` (미커밋)
- 기준: `lld2-analysis-candidates_before.md`
- 테스트: `./gradlew cleanTest test` — tests 133 / failures 0 / errors 0 (점검 시점 실행, 실제 Qdrant 기동 상태)
- 경로 앞부분 `src/main/java/com/iot/project/` 생략, 행 번호는 점검 시점 기준

## 요약

| 판정 | 개수 | 항목 |
|---|---|---|
| 부합 | 33 | 아래 표 |
| 부합(수동 확인 필요) | 3 | R11, C6, C7 |
| 변경 구현 | 2 | A1-2, C8 |
| 미부합 | 0 | |
| 확인 불가 | 0 | |

## API

| ID | 요구사항 | 로직 위치 | 판정 | 근거·이유 |
|---|---|---|---|---|
| A1 | `POST /api/quality-cases/{id}/analyses` 202 `{analysisId, status: "PENDING"}`, 실패 404/409/409/400 | `Analysis/AnalysisController.java` `request`(202), `Analysis/AnalysisService.java:31` `request`, `Analysis/AnalysisAcceptedResponse.java:4` | 부합 | `AnalysisControllerTest.분석_요청시_202_PENDING_분석_생성` 외 6개. 503 분기 추가는 아래 "요구사항 밖" |
| A1-0 | 검사 순서 404 → RESOLVED 409 → 진행 중 409 → 토큰 400 | `AnalysisService.java:32~42` | 부합 | 코드 순서 그대로 / `17-analysis-request.md` |
| A1-1 | 진행 중이면 409, 메시지 "이미 등록된 분석 요청입니다", 본문에 기존 `analysisId` | `AnalysisService.java:39~41`, `Analysis/AnalysisAlreadyRequestedException.java:9~10` | 부합 | `AnalysisControllerTest.진행중_분석이_있으면_409와_기존_analysisId` (detail 문구·`$.analysisId` 단언) |
| A1-2 | 동시 요청 시 하나만 202, 나머지 409 (UNIQUE 위반을 409로 변환) | `AnalysisService.java:32` `findByIdForUpdate` | **변경 구현** | 결과(202 1건 + 409 나머지)는 만족하나 방식이 다름 → 아래 상세 / `AnalysisConcurrencyTest` 3회 통과 |
| A2 | `GET /api/analyses/{analysisId}` 200 상태+후보, 404 없음 / 404 대상 삭제 | `AnalysisService.java:65` `findById` | 부합 | `AnalysisControllerTest.조회시_...`, `없는_분석_조회_404`, `대상_사례가_삭제되면_...` |
| A2-1 | 응답에 상태, `errorMessage`, 후보(`qdrantRank`, 사례 id·`caseCode`·제목, `vectorScore`, `invalidReason`, `jevScore`·`isNull`), `qdrantRank` 오름차순 | `Analysis/AnalysisResponse.java:11~41`, `AnalysisCandidate/AnalysisCandidateRepository.java` `findWithCaseByAnalysisId`(`order by c.qdrantRank`) | 부합 | `AnalysisControllerTest.조회시_상태와_후보를_Qdrant_순위대로` (순서·caseCode 단언). 응답에 `id`·`qualityCaseId`·`createdAt`도 있음(아래 "요구사항 밖") |
| A2-2 | 대상 사례 복구하면 다시 200 | `AnalysisService.java:67` (`deleted_at`만 봄) | 부합 | `AnalysisControllerTest.대상_사례가_삭제되면_조회_404_복구하면_200` |

## 도메인 규칙

| ID | 요구사항 | 로직 위치 | 판정 | 근거·이유 |
|---|---|---|---|---|
| R1 | 분석 대상은 OPEN만 (요청 시점) | `AnalysisService.java:35~37` | 부합 | `AnalysisControllerTest.RESOLVED_사례_409` |
| R2 | 사례당 Analysis 최대 1개 | `AnalysisService.java:32·38~49`, V4 `uk_analysis_target_quality_case` | 부합 | `AnalysisConcurrencyTest` (분석 행 1개 단언) |
| R3 | 끝난 분석이 있으면 Analysis·Candidate 삭제 후 새로 삽입, 한 트랜잭션 | `AnalysisService.java:45~49` (`@Transactional` 30행), `AnalysisRepository.java:28` `deleteNow`, `AnalysisCandidateRepository.java:20` | 부합 | `AnalysisControllerTest.끝난_분석이_있으면_후보까지_지우고_새로_만든다` (id 바뀜·옛 후보 0건) |
| R4 | 요청 시 토큰 동기 검사, 초과 400, 분석 안 만들고 기존 분석도 안 지움 | `AnalysisService.java:42`(삭제 45행보다 앞), `:53` `checkQueryLength`, `embedding/EmbeddingClient.java:55` | 부합 | `AnalysisControllerTest.토큰_상한_초과시_400_기존_분석은_그대로` |
| R5 | 처리기는 실행 시점 사례로 `toQueryText`, DB 저장 안 함 | `Analysis/AnalysisWorker.java:118~120` | 부합 | `AnalysisWorkerTest.실행_시점_사례로_검색하고_대상이_삭제됐어도_진행` (수정한 제목으로 `embedQuery` 호출 단언). `query_text` 컬럼 없음(V4) |
| R6 | 실행 시점에 대상 삭제·해결돼도 확인 안 하고 진행 | `AnalysisWorker.java:118` `findWithRefsById`(삭제 포함 조회), 상태 검사 없음 | 부합 | 같은 테스트 (대상 삭제 후 NO_CANDIDATES로 끝남). 해결된 경우는 테스트 없으나 상태 검사 코드 자체가 없음 |
| R7 | Qdrant top-10 결과 중 MySQL에 있는 id는 전부 저장 | `AnalysisWorker.java:125`(`TOP_K` 10), `:133~147` | 부합 | `AnalysisWorkerTest.후보를_전부_저장하고_근거로_못쓰는_후보는_사유를_남긴다` |
| R8 | 삭제 → 미해결 → 버전 순서, 처음 걸린 하나만, 통과하면 NULL | `AnalysisWorker.java:162` `invalidReason` | 부합 | 같은 테스트 (삭제+미해결 겹침 → DELETED만) |
| R9 | MySQL에 없는 id는 저장 안 함, 로그에는 남김 | `AnalysisWorker.java:137~140` | 부합 | 같은 테스트 (3위 제외, 순위 1·2·4·5). 로그 내용은 테스트 없음 |
| R10 | NULL 후보 1개 이상 CANDIDATES_READY, 0개(후보 없음 포함) NO_CANDIDATES | `AnalysisWorker.java:148~149` | 부합 | `후보를_전부_저장...`(READY), `근거로_쓸_후보가_없으면_NO_CANDIDATES`, `실행_시점_...`(Qdrant 0건 → NO_CANDIDATES) |
| R11 | 임베딩 실패, Qdrant 실패·타임아웃, 처리기 토큰 초과 → 즉시 FAILED + `error_message`, 재시도 없음 | `AnalysisWorker.java:103~109` (`process` 전체 catch → `fail`) | 부합(수동 확인 필요) | 임베딩·Qdrant 실패는 `AnalysisWorkerTest` 2개로 확인. **처리기 토큰 초과와 타임아웃은 같은 catch 경로지만 자동 테스트 없음** (토큰 초과는 mock으로 추가 가능, 타임아웃은 C7) |
| R12 | `error_message`는 FAILED일 때만 | `AnalysisRepository.java:52`(`finish`는 error null), `:56`(`fail`만 error 기록) | 부합 | `근거로_쓸_후보가_없으면_NO_CANDIDATES`, `후보를_전부_저장...`에서 `error_message` null 단언 |
| R13 | 저장된 후보는 후보 사례가 삭제·취소돼도 그대로 | 후보 삭제 경로는 `AnalysisService.java:46`(재요청 시)뿐, `QualityCaseService.delete`는 논리 삭제 | 부합 | 코드에 다른 삭제 경로 없음 / `18-analysis-read.md` |

## 데이터·스키마

| ID | 요구사항 | 로직 위치 | 판정 | 근거·이유 |
|---|---|---|---|---|
| D1 | `analysis.query_text` 삭제 | `db/migration/V4__analysis_lld2.sql` | 부합 | Flyway v4 적용(`flyway_schema_history` 확인), `Analysis` 엔티티에 필드 없음, `ddl-auto=validate` 기동 통과 |
| D2 | `target_quality_case_id` UNIQUE | V4 `uk_analysis_target_quality_case` | 부합 | 잠금 제거 실험에서 이 제약 위반이 발생해 존재 확인 |
| D3 | `error_message TEXT NULL` | V4, `Analysis/Analysis.java` `errorMessage` | 부합 | 실패 테스트에서 값 기록 단언 |
| D4 | enum PENDING/SEARCHING/CANDIDATES_READY/NO_CANDIDATES/FAILED, 옛 값 제거 | `Analysis/AnalysisStatus.java` | 부합 | 옛 값(ANALYZING·COMPLETED·INSUFFICIENT_EVIDENCE) 없음 |
| D5 | `is_null` NULL 허용, 이번엔 항상 NULL | V4 `MODIFY is_null BOOLEAN NULL`, `AnalysisCandidate` 생성자가 `isNull`을 설정하지 않음 | 부합 | `후보를_전부_저장...` (`is_null` 전부 NULL 단언) |
| D6 | `invalid_reason VARCHAR(30) NULL`, enum DELETED/NOT_RESOLVED/STALE_VERSION | V4, `AnalysisCandidate/InvalidReason.java` | 부합 | 같은 테스트 (세 값 모두 저장 확인) |
| D7 | 후보 버전 저장 안 함 | `AnalysisCandidate/AnalysisCandidate.java` (버전 필드 없음) | 부합 | |
| D8 | 기존 `analysis` 행 없음 → 이전 불필요 | — | 부합 | 구현 전 사용자 확인, 점검 시 `analysis` 0건 |
| D9 | `created_at`은 앱 KST `LocalDateTime.now()` | `Analysis.java:60`, `AnalysisCandidate.java:74` | 부합 | 기본 시간대는 `main`에서 Asia/Seoul 고정 (기존) |

## 비동기·동시성

| ID | 요구사항 | 로직 위치 | 판정 | 근거·이유 |
|---|---|---|---|---|
| C1 | API 스레드: Analysis INSERT·재요청 삭제만 / 처리기: 상태·Candidate·`error_message`만 | `AnalysisService.java:45~49` / `AnalysisWorker.java:103·108·151~154` | 부합 | 쓰기 지점을 전수 확인. API는 상태를 바꾸지 않고, 처리기는 분석을 만들거나 지우지 않음 |
| C2 | PENDING 하나를 조건부 UPDATE로 SEARCHING 선점 | `AnalysisWorker.java:100~103`, `AnalysisRepository.java:47` `claim`(`move` 35행, `where status = :from`) | 부합 | `19-analysis-worker.md`. 선점 경쟁 자체는 단일 스레드라 테스트 없음 |
| C3 | 임베딩·Qdrant는 트랜잭션 밖, 후보 INSERT + 최종 상태는 한 트랜잭션 | `AnalysisWorker.java:123·125`(밖), `:151~154`(`transactionTemplate`) | 부합 | 구조로 확인. 중간 종료 자동 테스트 없음 |
| C4 | 기동 시 SEARCHING → PENDING | `AnalysisWorker.java:70~76` `start`, `:83`, `AnalysisRepository.java:60` | 부합 | `AnalysisWorkerTest.기동시_검색중이던_분석은_PENDING으로_되돌린다` |
| C5 | `analysis.poll-delay-ms` 기본 1000, `indexing.worker.enabled` 공유, 테스트는 `pollOnce()` 직접 | `AnalysisWorker.java:57~58` | 부합 | test config에서 꺼져 `AnalysisControllerTest`의 분석이 PENDING으로 남음 |
| C6 | 임베딩 계산은 한 번에 1건 (모든 호출처 공통 잠금) | `embedding/EmbeddingClient.java:71` `synchronized (embedLock)` | 부합(수동 확인 필요) | 세 호출처 모두 `embed()`를 지나므로 공통. 실제 모델이 필요해 자동 테스트 없음 |
| C7 | Qdrant 타임아웃 연결 2초·응답 5초, `QdrantClient` 상수 | `qdrant/QdrantClient.java:22~23`, `:34~37` | 부합(수동 확인 필요) | `QdrantClientTest`가 실제 Qdrant로 정상 경로 통과. 타임아웃 발생은 테스트 없음 |
| C8 | **완료 로그**에 분석 id, 쿼리 텍스트, 후보 전체, 단계별 소요 시간 | `AnalysisWorker.java:120`(시작 로그), `:155~158`(완료 로그) | **변경 구현** | 쿼리 텍스트가 완료 로그가 아니라 시작 로그에 있음 → 아래 상세 |

## 기타

| ID | 요구사항 | 로직 위치 | 판정 | 근거·이유 |
|---|---|---|---|---|
| E1 | `isBlank` → `isNullOrBlank` + helper 주석 | `QualityCase/QualityCaseService.java:226~228` | 부합 | 호출처 98행 함께 변경, 기존 테스트 통과 |

## 변경 구현 상세

- **A1-2 동시 요청 처리**
  - 요구사항: UNIQUE 위반을 409로 변환
  - 구현: 사례 행을 `FOR UPDATE`로 잠가 같은 사례 요청을 줄 세움. 뒤 요청은 앞 요청이 커밋한 PENDING을 보고 A1-1과 같은 409(기존 id 포함). UNIQUE는 안전망으로 남김
  - 이유: UNIQUE 위반 뒤에는 같은 트랜잭션에서 기존 `analysisId`를 다시 읽을 수 없어 A1-1의 "본문에 기존 id"를 지킬 수 없음. resolve·수정과 같은 잠금 방식
  - 영향: 잠금을 우회해 UNIQUE에 걸리면 409가 아니라 500 (현재 그런 경로 없음)
  - 합의: **합의됨 (2026-10-31)** — 사용자: 잠금이든 UNIQUE든 상관없지만 같은 결과라면 Spring(애플리케이션)에서 막는 편이 좋다 → 행 잠금 유지
- **C8 로그 위치**
  - 요구사항: 완료 로그 한 줄에 쿼리 텍스트 포함
  - 구현: 쿼리 텍스트는 처리 시작 로그(120행), 후보·소요 시간은 완료 로그(155행). 둘 다 `analysisId`가 있어 이어 볼 수 있음
  - 이유: 실패(FAILED)한 분석도 어떤 텍스트로 검색했는지 남기려면 시작 시점에 찍어야 함 (완료 로그는 성공 때만 나감)
  - 합의: **합의됨 (2026-10-31)** — 사용자: 쿼리 텍스트는 저장하지 않는 단순 확인용이라 시작·완료 어느 로그에 있든 상관없음 → 시작 로그 유지

## 요구사항 밖에서 추가된 동작

- **503 분기**: 요청 시 토크나이저를 못 불러오면 `TokenCheckUnavailableException`(503) — `AnalysisService.java:58~60`. resolve·수정과 같은 처리, 자동 테스트 없음
- **조회 응답 필드**: A2-1 목록 외에 `id`, `qualityCaseId`, `createdAt`이 응답에 있음 — `AnalysisResponse.java:12~16`
- **409 메시지**: "이미 등록된 분석 요청입니다." 뒤에 `qualityCaseId`·`analysisId`가 붙음 — `AnalysisAlreadyRequestedException.java:9`
- **기존 코드 동작 변화**
  - `EmbeddingClient` 잠금으로 `/similar-cases`(`QualityCaseService.findSimilar`, `@Transactional(readOnly = true)`)가 다른 임베딩을 기다리는 동안 DB 커넥션을 쥠. 인덱싱 처리기도 분석과 번갈아 계산
  - Qdrant 타임아웃이 인덱싱·검색 노출 처리기와 `/similar-cases`에도 적용 (C7의 범위가 분석만이 아님)
- **삭제된 후보 노출**: 분석 조회 응답에 삭제된 후보의 `caseCode`·제목이 그대로 나옴 — `AnalysisResponse.java` `Candidate.from`. LLD 4 404 규칙의 예외로 **합의됨 (2026-11-02)**. `invalidReason`은 분석 당시 판단이라 이후 변경을 반영하지 않으며, LLM 단계에서 넘기기 직전 재검증 필요
- **접근 제한자 변경**: `QualityCaseDocument.toQueryText`가 `public`이 됨 (분석 패키지에서 호출)
- **순서도**: `10-embedding.md`를 다시 그림 (기존 그림이 6144토큰 자르기·호출처 없음으로 옛 내용이었음)
- **테스트 정리 코드**: `ResolveTestData.deleteAll`이 `analysis`·`analysis_candidate`도 지움

## 미정·나중에 항목 확인

- SSE, JEV 평가 상태 컬럼, LLM용 상태(`ANALYZING`·`COMPLETED`), 로그 형식 통일, 임베딩 잠금 해제·세션 추가: 모두 **구현되지 않음** (범위 초과 없음)
- "만들지 않는 것"의 테스트 축소, Qdrant 단계 재시작 JUnit, 인덱싱 이름 변경: 손대지 않음

## 사용자 확인 필요

1. 수동 확인
   - **C6**: 실제 모델로 분석 요청과 RESOLVED 수정(인덱싱)을 겹쳐 실행 → 로그의 임베딩 시간이 한쪽씩 늘어나는지, RSS가 1건 수준에 머무는지
   - **C7 / R11 타임아웃**: `docker pause <qdrant 컨테이너>` 후 분석 요청 → 약 5초 뒤 `GET /api/analyses/{id}`가 FAILED, `errorMessage`에 타임아웃 예외. 끝나면 `docker unpause`
   - **R11 처리기 토큰 초과**: 분석 요청 후 처리 전에 OPEN 사례 내용을 1024토큰 넘게 수정 → FAILED (폴링 1초라 타이밍이 어려움, mock 테스트 추가가 더 쉬움)

## 나중에 확인할 것

- **`DELETED`·`NOT_RESOLVED`가 실제로 나타나는지** (2026-11-02 결정: 값은 유지하고 실제 운영·측정에서 확인)
  - 평소에는 Qdrant 필터가 먼저 걸러 드문 것이 정상이고, 자주 또는 삭제·취소 후 오래 지나서 나타나면 검색 노출 처리기 이상 신호
  - 확인: `SELECT invalid_reason, COUNT(*) FROM analysis_candidate GROUP BY invalid_reason;` (재분석 때 이전 후보가 지워지므로 현재 남은 분석만 집계됨. 누적 빈도는 분석 완료 로그로 확인)

