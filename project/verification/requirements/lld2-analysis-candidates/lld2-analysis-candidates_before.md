# LLD 2 분석 요청·유사 사례 후보 저장 요구사항

- 상태: 구현 전 확정본 (2026-10-31). 구현 후 점검은 같은 폴더의 `lld2-analysis-candidates_after.md`
- 출처: `/home/minsung/Documents/Backend/개인 프로젝트/설계/LLD 정합화/LLD2 기반 설계 구체화/`(`LLD2 설계 확정안.md`, `LLD2 Q&A.md`), `설계/LLD/성능_점검_기준_및_테스트_계획.md` 1-1절, 대화 (2026-10-31)

## 범위
- 만드는 것: 분석 요청·조회 API, `AnalysisWorker`(쿼리 임베딩 → Qdrant 검색 → MySQL 검증 → 후보 저장), 임베딩 동시 1건 잠금, Flyway V4, Qdrant RestClient 타임아웃, `isBlank` helper 이름 변경
- 만들지 않는 것: JEV/Reranker, LLM, SSE, 로그 형식 통일, 테스트 축소(보류), Qdrant 단계 재시작 JUnit(넣지 않기로 함), 인덱싱 이름 변경(프로젝트 완성 후)
- 그대로 두는 것: `GET /api/quality-cases/{id}/similar-cases` (디버그·성능 측정용)

## API
| ID | Method | 경로 | 성공 | 실패 |
|---|---|---|---|---|
| A1 | POST | `/api/quality-cases/{id}/analyses` | 202 `{ analysisId, status: "PENDING" }` | 404 사례 없음·삭제됨 / 409 RESOLVED / 409 진행 중 분석 있음 / 400 토큰 상한 초과 |
| A2 | GET | `/api/analyses/{analysisId}` | 200 상태 + 후보 목록 | 404 분석 없음 / 404 대상 사례 삭제됨 |

- [ ] A1-0. 검사 순서: 사례 없음·삭제(404) → RESOLVED(409) → 진행 중 분석(409) → 토큰 초과(400)
- [ ] A1-1. 진행 중 분석이 있으면 409, 메시지 "이미 등록된 분석 요청입니다", 응답 본문에 기존 `analysisId` 포함
- [ ] A1-2. 같은 사례에 동시 요청 시 하나만 202, 나머지는 A1-1과 같은 409 (UNIQUE 위반을 409로 변환)
- [ ] A2-1. 응답에 상태, `errorMessage`(FAILED일 때), 후보 목록 포함. 후보 항목: `qdrantRank`, 후보 사례 id·`caseCode`·제목, `vectorScore`, `invalidReason`, `jevScore`·`isNull`(지금은 null). 후보는 `qdrantRank` 오름차순
- [ ] A2-2. 대상 사례를 복구하면 A2가 다시 200

## 도메인 규칙
- [ ] R1. 분석 대상은 OPEN 사례만 (요청 시점 검사)
- [ ] R2. 사례당 Analysis는 최대 1개
- [ ] R3. 끝난 분석(CANDIDATES_READY·NO_CANDIDATES·FAILED)이 있을 때 재요청하면 기존 Analysis와 그 Candidate를 삭제하고 새 Analysis를 삽입한다 (갱신 아님, id 바뀜). 삭제와 삽입은 한 트랜잭션
- [ ] R4. 요청 시 `QualityCaseDocument.toQueryText` 토큰 수를 동기 검사, 1024 초과면 400이고 Analysis를 만들지 않는다 (기존 분석도 지우지 않음)
- [ ] R5. 처리기는 **실행 시점**의 사례로 `toQueryText`를 만들고 DB에 저장하지 않는다 (로그만)
- [ ] R6. 처리기는 실행 시점에 대상 사례가 삭제·해결됐어도 확인하지 않고 그대로 진행한다
- [ ] R7. Qdrant top-10(`searchable=true` 필터)이 돌려준 후보는 MySQL에 있는 id면 **전부** 저장한다
- [ ] R8. `invalid_reason`은 삭제(`DELETED`) → 미해결(`NOT_RESOLVED`) → 버전 불일치(`STALE_VERSION`, payload `sourceVersion` ≠ 현재 `source_version`) 순서로 확인하고 처음 걸린 하나만 기록한다. 모두 통과하면 NULL
- [ ] R9. MySQL에 없는 id는 저장하지 않는다 (FK). 로그에는 남긴다
- [ ] R10. NULL인 후보가 1개 이상이면 CANDIDATES_READY, 0개면(후보 없음 포함) NO_CANDIDATES. 둘 다 정상 결과
- [ ] R11. 임베딩 실패, Qdrant 실패·타임아웃, 처리기 토큰 초과는 즉시 FAILED + `error_message`. 자동 재시도 없음
- [ ] R12. `error_message`는 분석이 FAILED일 때만 채운다. 후보가 걸러진 것은 실패가 아니다
- [ ] R13. 저장된 후보는 이후 후보 사례가 삭제·해결 취소돼도 그대로 둔다 (논리 삭제라 FK 문제 없음)

## 데이터·스키마
- [ ] D1. Flyway V4: `analysis.query_text` 삭제
- [ ] D2. Flyway V4: `analysis.target_quality_case_id` UNIQUE
- [ ] D3. Flyway V4: `analysis.error_message TEXT NULL` 추가
- [ ] D4. `AnalysisStatus` = PENDING / SEARCHING / CANDIDATES_READY / NO_CANDIDATES / FAILED (기존 ANALYZING·COMPLETED·INSUFFICIENT_EVIDENCE 제거)
- [ ] D5. Flyway V4: `analysis_candidate.is_null` NULL 허용 (NULL = 미평가), 이번엔 항상 NULL
- [ ] D6. Flyway V4: `analysis_candidate.invalid_reason VARCHAR(30) NULL` 추가, enum `DELETED`/`NOT_RESOLVED`/`STALE_VERSION`
- [ ] D7. 후보 버전(`candidate_source_version`)은 저장하지 않는다
- [x] D8. 기존 `analysis` 행 없음 → 데이터 이전 불필요 — 사용자 확인 (2026-10-31)
- [ ] D9. `created_at`은 앱이 KST `LocalDateTime.now()`로 넣는다

## 비동기·동시성
- [ ] C1. 쓰는 주체: API 스레드는 Analysis INSERT(PENDING)·재요청 시 삭제만, `AnalysisWorker`(전용 단일 스레드, 자체 `ScheduledExecutorService`)는 상태 변경·Candidate INSERT·`error_message`만 쓴다
- [ ] C2. 처리기는 PENDING 하나를 조건부 UPDATE로 SEARCHING 선점한 뒤 처리한다
- [ ] C3. 임베딩·Qdrant 호출은 트랜잭션 밖. Candidate 전부 INSERT + 최종 상태 변경은 한 트랜잭션
- [ ] C4. 기동 시 SEARCHING → PENDING으로 되돌려 처음부터 다시 처리
- [ ] C5. `analysis.poll-delay-ms` 기본 1000(코드 기본값), `indexing.worker.enabled`를 공유해 테스트에서 끄고 `pollOnce()` 직접 호출
- [ ] C6. `EmbeddingClient`의 임베딩 계산은 한 번에 1건 (인덱싱·분석·`/similar-cases` 모든 호출처 공통 잠금)
- [ ] C7. Qdrant RestClient 타임아웃 연결 2초·응답 대기 5초, `QdrantClient` 상수 (설정 키 아님)
- [ ] C8. 완료 로그에 분석 id, 쿼리 텍스트, 후보 전체(순위, 사례 id, score, `invalid_reason`, MySQL에 없는 id 포함), 단계별 소요 시간(임베딩·Qdrant·저장)

## 기타
- [ ] E1. `QualityCaseService.isBlank` → `isNullOrBlank`, 위에 `// helper: String.isBlank()와 달리 null도 true` 주석

## 미정·나중에
- SSE: LLM 단계에서 진행 표시가 필요하면 폴링부터, 불편할 때만 검토
- JEV 평가 상태 컬럼, LLM용 상태(`ANALYZING`·`COMPLETED`) 추가
- 로그 형식 통일 (C8 로그 포함)
- 타임아웃·poll 간격·Top-K는 가상 사례 생성기 측정 후 재조정
- 임베딩 잠금을 풀고 세션을 늘릴지는 측정 후

## 주의
- 후보는 "검증 통과분만 저장"이 아니라 **전부 저장** + `invalid_reason` (처음 제안에서 뒤집힘)
- 검색 텍스트는 요청 시점이 아니라 **실행 시점** 사례로 만들고 저장하지 않는다 (`query_text` 컬럼 삭제, 처음 제안에서 뒤집힘)
- 진행 중 재요청은 409지만 끝난 뒤 재요청은 409가 아니라 교체 (R3)
- 처리기에서 대상 사례 삭제·해결을 FAILED로 처리하지 않는다 (R6, 처음 안에서 뒤집힘)
- `error_message`와 `invalid_reason`은 무관 (R12)
