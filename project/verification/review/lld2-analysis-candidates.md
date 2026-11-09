# 코드 검토 체크리스트 (LLD 2 분석 요청·후보 저장)

- 목적: 모든 코드를 읽지 않고, 설계 의도·데이터 흐름에 필요한 핵심 코드만 직접 확인
- 범위: `feature/lld2-analysis`의 변경분 (main 대비 + 미커밋), 작성 2026-10-31
- 경로 앞부분 `src/main/java/com/iot/project/` 생략, 행 번호는 작성 시점 기준
- 근거: `verification/requirements/lld2-analysis-candidates/`(`_before.md` 요구사항 ID, `_after.md` 점검 결과), `설계/LLD 정합화/LLD2 기반 설계 구체화/LLD2 설계 확정안.md`
- 우선순위: **필수**(LLD 결정 사항 보장 여부) / **중요**(경계 상황 정합성) / **낮음**(패턴 확인)
- 추천 순서: 4-1 → 2-1 → 1-1 → 2-2 → 3-1 → 1-2 → 4-3 → 2-3 → 5-1 → 5-2 → 1-3 → 4-2 → 2-4 → 5-3
  - 처리기 전체 흐름(4-1)을 먼저 잡고, 그 흐름이 기대는 트랜잭션(2-1)과 요청 쪽 판단(1-1·2-2) 순서로. 4-3·5-1은 기존 코드 동작까지 바꾸는 변경이라 따로 본다

### 1. 비즈니스 규칙과 조건 분기

- [ ] **1-1** `Analysis/AnalysisService.java:31` `request` — 필수
  - 역할: 분석 요청 접수. 사례 확인 → 진행 중 분석 확인 → 토큰 검사 → 끝난 분석 교체 → PENDING INSERT
  - LLD: A1-0·A1-1·R1·R3·R4 / LLD 2 §3 1~3번
  - 확인
    1. 검사 순서(32~42행): 없음·삭제 404 → RESOLVED 409 → 진행 중 409 → 토큰 400. 순서가 바뀌면 같은 요청이 다른 코드를 받는다 (예: 진행 중 + 토큰 초과 → 409가 아니라 400)
    2. 39행 `isInProgress()`가 "막을지(409) / 교체할지"를 가르는 유일한 기준 → 3-1의 enum 정의와 함께 본다
    3. 토큰 검사(42행)가 **기존 분석 삭제(45~48행)보다 먼저** → 400이면 아무것도 지우지 않음. 삭제가 먼저였어도 롤백되긴 하지만, 순서 자체가 "실패한 요청은 아무것도 바꾸지 않는다"를 코드로 드러냄
  - 잘못되면: 진행 중 분석을 교체 → 처리기가 이미 지워진 분석을 처리(후보 INSERT가 FK 위반으로 FAILED 기록도 못 함) / 토큰 400인데 기존 분석이 사라짐
  - 근거: `AnalysisControllerTest`(10개 중 요청 7개) / 다이어그램 `17-analysis-request.md`

- [ ] **1-2** `Analysis/AnalysisWorker.java:133~149` 후보 루프 + `:162` `invalidReason` — 필수
  - 역할: Qdrant 결과를 순서대로 돌며 MySQL에 있으면 사유를 붙여 저장 목록에 넣고, 없으면 로그만. 근거로 쓸 후보가 있는지로 최종 상태 결정
  - LLD: R7·R8·R9·R10 / LLD 2 §3 6~7번, §5
  - 확인
    1. `invalidReason`: 삭제 → 미해결 → 버전 순서, `return`으로 처음 걸린 하나만
    2. 137행 MySQL에 없는 id는 `continue` → 저장 안 하지만 **순위(`rank = i + 1`)는 Qdrant 순서 그대로** 유지 (저장된 순위에 빈 번호가 생김)
    3. 148행 `anyUsable`: 사유 NULL이 하나라도 있으면 CANDIDATES_READY. Qdrant가 0건이어도, 전부 걸러져도 NO_CANDIDATES
    4. 버전 비교 대상은 Qdrant payload의 `sourceVersion`(벡터를 만든 버전)과 MySQL 현재 버전
  - 잘못되면: 순서가 바뀌면 삭제된 사례가 NOT_RESOLVED로 기록 / 순위를 저장 목록 기준으로 매기면 Qdrant 순위가 어긋남 / 걸러진 후보만 있는데 CANDIDATES_READY
  - 근거: `AnalysisWorkerTest.후보를_전부_저장하고_근거로_못쓰는_후보는_사유를_남긴다`, `근거로_쓸_후보가_없으면_NO_CANDIDATES` / `19-analysis-worker.md`

- [ ] **1-3** `Analysis/AnalysisService.java:65` `findById` — 중요
  - 역할: 분석 조회. 대상 사례가 삭제됐으면 404
  - LLD: A2·A2-2 / LLD 4 "삭제된 사례는 restore 외 모든 API에서 404"
  - 확인: 삭제 판단은 **대상 사례**의 `deleted_at`(분석 자체엔 삭제 개념 없음). 후보 사례가 삭제된 건 막지 않음(R13, 응답에 그대로 나옴)
  - 잘못되면: 삭제된 사례의 분석이 조회됨 → 존재 여부 노출
  - 근거: `AnalysisControllerTest.대상_사례가_삭제되면_조회_404_복구하면_200` / `18-analysis-read.md`

### 2. 트랜잭션과 데이터 정합성

- [ ] **2-1** `Analysis/AnalysisWorker.java:151` `transactionTemplate` 블록 — 필수
  - 역할: 후보 전부 INSERT + 최종 상태(SEARCHING → CANDIDATES_READY/NO_CANDIDATES)를 한 트랜잭션으로
  - LLD: C3 / LLD 2 §7 "Candidate 저장과 상태 반영의 원자성"
  - 확인
    1. 임베딩(123행)·Qdrant(125행)는 트랜잭션 밖, MySQL 쓰기만 이 블록 안
    2. 사이에 종료되면 후보도 상태도 없이 SEARCHING으로 남음 → 기동 시 PENDING → 처음부터 다시 (4-2)
  - 잘못되면: 따로 커밋하면 후보만 저장된 채 SEARCHING으로 남고, 재기동 후 다시 처리할 때 같은 후보 INSERT가 `uk_analysis_candidate` 위반 → FAILED
  - 근거: 중간 종료 자동 테스트 없음 (구조로 확인) / `19-analysis-worker.md`

- [ ] **2-2** `Analysis/AnalysisService.java:45~48` 기존 분석 삭제 + `AnalysisRepository.java:28` `deleteNow`, `AnalysisCandidate/AnalysisCandidateRepository.java:20` `deleteByAnalysisIdNow` — 필수
  - 역할: 끝난 분석이 있으면 후보 → 분석 순서로 지우고 같은 트랜잭션에서 새 분석 INSERT
  - LLD: R3 (갱신이 아니라 삭제 후 삽입)
  - 확인
    1. 엔티티 `delete()`가 아니라 JPQL 벌크 삭제인 이유: Hibernate는 flush 때 INSERT를 DELETE보다 먼저 보내 → 같은 사례의 새 분석이 `uk_analysis_target_quality_case`에 걸림. 벌크 삭제는 그 자리에서 바로 실행
    2. 후보 먼저(FK). 둘 다 `flushAutomatically = true`
    3. 이 세 문장이 1-1의 트랜잭션 안 → 뒤에서 실패하면 삭제도 롤백
  - 잘못되면: 재요청할 때마다 500 (UNIQUE 위반) / 후보가 남아 FK 위반
  - 근거: `AnalysisControllerTest.끝난_분석이_있으면_후보까지_지우고_새로_만든다`

- [ ] **2-3** `Analysis/AnalysisService.java:32` `findByIdForUpdate` + Flyway V4 `uk_analysis_target_quality_case` — 중요
  - 역할: 같은 사례 동시 요청을 사례 행 잠금으로 줄 세움. UNIQUE는 안전망
  - LLD: A1-2·R2 (설계는 "UNIQUE 위반 → 409", 구현에서 잠금으로 변경 — 확정안·요구사항 "주의"에 기록)
  - 확인
    1. 잠금 범위: 32행부터 트랜잭션 끝까지. 뒤 요청은 앞 요청 커밋 후 깨어나 39행에서 앞 요청의 PENDING을 보고 409
    2. 잠금을 쥔 채 토큰 검사(42행) → 토크나이저 첫 로딩이면 그동안 같은 사례의 resolve·수정·삭제도 대기 (resolve의 `checkDocumentLength`와 같은 구조)
    3. 처리기는 이 잠금을 쓰지 않음 (사례를 읽기만 함)
  - 잘못되면: 잠금 없이 UNIQUE만 있으면 뒤 요청이 500 (잠금을 빼고 돌려 재현 확인함)
  - 근거: `AnalysisConcurrencyTest` (3회 반복 통과, 잠금 제거 시 실패 확인)
  - 열린 질문: 잠금을 우회해 UNIQUE에 걸리는 경로는 지금 없지만, 걸리면 409가 아니라 500. 이대로 둘지

- [ ] **2-4** `src/main/resources/db/migration/V4__analysis_lld2.sql` — 낮음
  - 역할: `query_text` 삭제, `error_message` 추가, 사례당 UNIQUE, `is_null` NULL 허용, `invalid_reason` 추가
  - LLD: D1~D6
  - 확인: 기존 행 0건이라 데이터 이전 없음(D8). 엔티티와 컬럼 이름·NULL 여부 일치 (`ddl-auto=validate`가 기동 때 검사)

### 3. 상태 전환

- [ ] **3-1** `Analysis/AnalysisRepository.java:35` `move` 및 `claim`(47)·`finish`(51)·`fail`(55)·`resetSearching`(60) + `AnalysisStatus.java:16` `isInProgress` — 필수
  - 역할: 모든 상태 변경이 `where status = :from` 조건부 UPDATE. PENDING → SEARCHING → 끝난 상태
  - LLD: D4·C2·C4 / 확정안 §4 상태
  - 확인
    1. `finish`·`fail`은 SEARCHING일 때만 바뀜. `claim`은 바뀐 행이 1일 때만 내 것
    2. `isInProgress`(PENDING·SEARCHING)가 1-1의 409/교체 기준. 상태를 추가할 때(LLM 단계의 ANALYZING) 여기에 넣지 않으면 분석 중에 교체됨
    3. `finish`의 반환값(바뀐 행 수)은 보지 않음 → 0건이어도 후보는 커밋됨. 단일 인스턴스에서는 SEARCHING이 다른 곳에서 바뀌지 않아 생기지 않는 경우
  - 잘못되면: 조건 없는 UPDATE면 늦게 끝난 실패 기록이 끝난 상태를 덮음
  - 근거: `AnalysisWorkerTest` 상태 단언 전체

### 4. 비동기 처리·동시성·재시도

- [ ] **4-1** `Analysis/AnalysisWorker.java:99` `pollOnce`, `:114` `process` — 필수 (가장 먼저 권장)
  - 역할: PENDING 하나 선점 → 실행 시점 사례로 쿼리 텍스트 → 임베딩 → Qdrant → 1-2 → 2-1. 예외는 전부 FAILED, 재시도 없음
  - LLD: R5·R6·R11·C1·C2 / LLD 2 §3 4~9번, §8
  - 확인
    1. 118행 `findWithRefsById`: 삭제된 사례도 읽는 쿼리 → 대상 삭제·해결을 확인하지 않고 진행 (R6). 연관 엔티티 `join fetch`라 트랜잭션 밖에서 텍스트 생성 가능
    2. 106행 catch가 `process` 전체를 감쌈 → 임베딩·Qdrant뿐 아니라 MySQL 오류도 FAILED. `fail` 자체가 실패하면 SEARCHING으로 남고 기동 시 복구
    3. 인덱싱 처리기와 달리 `executor.schedule` 재시도가 없음 → 실패 즉시 스레드가 다음 분석으로
  - 잘못되면: catch가 없으면 SEARCHING으로 영영 남음(재기동 전까지 같은 사례 재요청도 409) / 요청 시점 텍스트를 쓰면 수정 전 내용으로 검색
  - 근거: `AnalysisWorkerTest` 6개 / `19-analysis-worker.md`

- [ ] **4-2** `Analysis/AnalysisWorker.java:70` `start`, `:83` `recoverInterrupted`, `:88` `pollSafely` — 낮음
  - 역할: 기동 시 SEARCHING → PENDING 후 1초 주기 폴링
  - LLD: C4·C5
  - 확인: `IndexingWorker`와 같은 구조 (이전 검토 4-2 참고). 다른 점은 켜고 끄는 키를 `indexing.worker.enabled`로 공유, 주기 키는 `analysis.poll-delay-ms`
  - 근거: `AnalysisWorkerTest.기동시_검색중이던_분석은_PENDING으로_되돌린다`

- [ ] **4-3** `embedding/EmbeddingClient.java:71` `embed`의 `synchronized (embedLock)` — 필수
  - 역할: ONNX 계산을 호출처와 무관하게 한 번에 1건
  - LLD: C6 / 확정안 Q5
  - 확인
    1. 잠금 범위는 텐서 생성 ~ `run`까지. 토큰화(`encode`)와 모델 로딩(`session()`)은 밖 → 토큰 검사(resolve·분석 요청)는 이 잠금을 기다리지 않음
    2. 로딩용 모니터(`this`, 85·105행)와 다른 객체 → 로딩 중이어도 계산 잠금과 엉키지 않음
    3. **기존 코드 영향**: `QualityCaseService.findSimilar`(184행)은 `@Transactional(readOnly = true)` 안에서 `embedQuery`(193행) → 잠금을 기다리는 동안 DB 커넥션을 쥠. 인덱싱 처리기도 이제 분석과 번갈아 계산
  - 잘못되면: 잠금이 없으면 동시 계산만큼 ONNX 메모리 수위가 오르고 반환 안 됨 / 잠금 범위에 로딩이 들어가면 첫 호출 7초 동안 토큰 검사까지 막힘
  - 근거: 테스트 없음 (실제 모델 필요), 수동 확인 필요 — 분석과 인덱싱을 겹쳐 실행해 로그의 임베딩 시간과 RSS 확인
  - 열린 질문: `/similar-cases`가 잠금 대기 중 DB 커넥션을 쥐는 것 — 디버그용이라 둘지, 측정 때 볼지

### 5. 외부 시스템 연동과 실패 처리

- [ ] **5-1** `qdrant/QdrantClient.java:34~37` 생성자 타임아웃 — 중요
  - 역할: JDK HttpClient로 연결 2초, 요청 팩토리로 응답 대기 5초
  - LLD: C7 / 성능 문서 1-1절
  - 확인
    1. 연결 타임아웃은 `HttpClient` 쪽, 응답 대기는 `JdkClientHttpRequestFactory.setReadTimeout` 쪽 (설정 위치가 다름)
    2. 이 `RestClient` 하나를 모든 호출이 씀 → 검색·upsert·setSearchable·컬렉션 확인 전부 적용. 처리기 3개 모두 영향
    3. 타임아웃 예외는 일반 예외와 같음 → `/similar-cases` 503, 분석 FAILED, 인덱싱·노출은 기존 재시도
  - 잘못되면: 타임아웃이 안 걸리면 Qdrant가 멈췄을 때 처리기 스레드가 영영 대기 → 분석이 SEARCHING으로 멈추고 같은 사례 재요청은 계속 409
  - 근거: `QdrantClientTest`가 실제 Qdrant로 통과(정상 경로만). 타임아웃 발생은 테스트 없음, 수동 확인 필요 — `docker pause` 후 분석 요청 → 약 5초 뒤 FAILED

- [ ] **5-2** `embedding/EmbeddingClient.java:55` `checkQueryLength` + `Analysis/AnalysisService.java:53` `checkQueryLength` — 중요
  - 역할: 요청 시 쿼리 텍스트 토큰 수 검사. 초과 400, 토크나이저를 못 불러오면 503
  - LLD: R4 / Q&A Q6
  - 확인
    1. `"query: "` 접두어를 붙여 셈 → 실제 임베딩 입력과 같은 길이
    2. 503 분기는 요구사항에 없던 것 (resolve의 `TokenCheckUnavailableException` 재사용) — 요구사항 "주의"에 기록
  - 잘못되면: 접두어를 빼고 세면 1022~1024토큰 경계에서 요청은 통과하고 처리기에서 FAILED
  - 근거: `AnalysisControllerTest.토큰_상한_초과시_400_기존_분석은_그대로` (503 분기는 테스트 없음)

- [ ] **5-3** `Analysis/AnalysisAlreadyRequestedException.java:10` — 낮음
  - 역할: 409 ProblemDetail에 `analysisId` 속성 추가
  - LLD: A1-1
  - 확인: `getBody().setProperty` → 응답 JSON 최상위에 `analysisId`. 프론트가 이 값으로 기존 분석을 조회
  - 근거: `AnalysisControllerTest.진행중_분석이_있으면_409와_기존_analysisId`

## 제외한 코드

- `Analysis/AnalysisController.java`: 서비스 호출과 상태 코드 지정만
- `Analysis/AnalysisAcceptedResponse.java`, `AnalysisResponse.java`: DTO 변환만 (후보 항목 구성은 1-3 근거 테스트로 확인됨)
- `Analysis/AnalysisNotFoundException.java`, `ResolvedQualityCaseAnalysisException.java`: 기존 예외 패턴 반복
- `Analysis/Analysis.java`, `AnalysisCandidate/AnalysisCandidate.java` 생성자, `InvalidReason`: 값 대입·enum 정의만 (규칙은 1-2에서)
- `AnalysisCandidate/AnalysisCandidateRepository.findWithCaseByAnalysisId`: 조회 전용 `join fetch` (N+1 없음)
- `QualityCase/QualityCaseDocument.toQueryText`: 접근 제한자만 `public`으로 변경 (내용은 이전 검토 1-4)
- `QualityCase/QualityCaseService.isNullOrBlank`: 이름 변경과 helper 주석만
- 테스트 코드: 각 항목의 근거로만 사용
