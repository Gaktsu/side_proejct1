# 코드 검토 체크리스트 (lld3-inspection-result)

- 목적: 모든 코드를 읽지 않고, 설계 의도·데이터 흐름에 필요한 핵심 코드만 직접 확인
- 범위: `feature/lld3-inspection-result`의 변경분 (main 대비 + 미커밋), 작성 2026-11-09
- 경로 앞부분 `src/main/java/com/iot/project/` 생략, 행 번호는 작성 시점 기준
- 근거: `verification/requirements/lld3-inspection-result/`(_before·_after), `/home/minsung/Documents/Backend/개인 프로젝트/설계/LLD 정합화/LLD3 기반 설계 구체화/`
- 우선순위: **필수**(LLD 결정 사항 보장 여부) / **중요**(경계 상황 정합성) / **낮음**(영향이 좁은 부가 로직)
- 추천 순서: 1-1 → 1-2 → 1-3 → 2-1 → 4-1 (입력 규칙과 분리 원칙을 먼저 보고, 삭제 규칙·응답 구조, 경합 순)

### 1. 비즈니스 규칙과 조건 분기

- [ ] **1-1** `InspectionResult/InspectionResultService.java:47` `validateValueAndSpec` — 필수
  - 역할: 등록·수정 공통으로 측정값과 규격의 관계를 검증한다
  - LLD: R6·R7·R8·R9·R10 / 기획서 §12 Validation
  - 확인
    1. 48행은 `numericValue`와 `textValue`가 모두 비었을 때만 거절하고, `hasText`라 공백뿐인 문자값도 "없음"으로 친다 → 둘 다 있는 입력은 통과한다
    2. 51~52행은 규격끼리만 비교하고 측정값은 비교 대상이 아니다 → 불량 값(규격 밖)이 거절되지 않고 저장된다
    3. `compareTo`를 쓰는 이유는 `BigDecimal`의 `equals`·부호 비교가 스케일에 영향받지 않게 하기 위함이다 (`10`과 `10.0`이 같은 규격으로 통과)
    4. `resultStatus`를 읽는 코드가 이 메서드에 없다 → 범위 밖 값 + PASS 조합도 저장되는 것이 의도다
  - 잘못되면: 규격을 벗어난 측정값이 400으로 거절되어 불량 기록을 남길 수 없게 됨
  - 근거: `InspectionResultControllerTest.규격_밖_측정값도_거절하지_않고_...`, `측정값이_숫자_문자_모두_없거나_공백이면_400_...` / 다이어그램 `20-inspection-result-create.md`

- [ ] **1-2** `InspectionResult/InspectionResultService.java:20~44` `create`·`update`·`delete` — 필수
  - 역할: 검사 결과를 사례 상태·버전·인덱싱과 분리해 저장한다
  - LLD: R4·R19·R20·R21 / LLD3 Q&A Q5·Q6
  - 확인
    1. 서비스 의존성이 저장소 2개(16~17행)뿐이다 → 임베딩·Qdrant·job 저장소를 호출할 경로가 없다
    2. 사례를 조회한 뒤 상태 분기가 없다 → OPEN·RESOLVED 구분 없이 동작한다. 이 허용은 "임베딩·LLM에 쓰지 않는다"는 전제 위에서만 안전하다
    3. `qualityCase`의 값을 바꾸는 호출이 없다 → `source_version`·`updated_at`이 그대로다
  - 잘못되면: 검사 결과를 고칠 때마다 사례 버전이 올라 불필요한 재임베딩이 돌거나, 나중에 LLM 입력으로 연결하면서 재분석 정책 없이 값이 바뀜
  - 근거: `InspectionResultControllerTest.RESOLVED_사례도_등록_수정_삭제_가능하고_버전과_인덱싱은_그대로`, `진행_중인_분석이_있어도_등록_가능`
  - 열린 질문: LLM 단계에서 검사 결과를 입력으로 쓰게 되면 이 전제가 깨지므로 RESOLVED 수정 허용을 다시 판단해야 함

- [ ] **1-3** `InspectionResult/InspectionResultRepository.java:14` `findByIdAndQualityCase_DeletedAtIsNull` — 중요
  - 역할: 수정·삭제 때 소속 사례가 논리 삭제된 검사 결과를 없는 것으로 다룬다
  - LLD: R2·R3·R5 / LLD 4 "삭제된 사례는 restore 외 404"
  - 확인
    1. 메서드 이름의 `QualityCase_DeletedAtIsNull`이 사례 조인 조건이다 → `findById`로 바꾸면 삭제된 사례의 검사 결과를 고칠 수 있게 된다
    2. 행을 지우지 않고 404로만 가리므로 사례를 복구하면 검사 결과가 그대로 돌아온다
  - 잘못되면: 삭제된 사례의 존재가 수정·삭제 응답으로 드러나거나, 복구 뒤 검사 결과가 이미 바뀌어 있음
  - 근거: `InspectionResultControllerTest.소속_사례가_논리_삭제되면_수정_삭제_404이고_행은_유지`, `사례_논리_삭제_후_복구하면_검사_결과가_다시_조회됨` / 다이어그램 `21`·`22`

### 2. 트랜잭션과 데이터 정합성

- [ ] **2-1** `QualityCase/QualityCaseService.java:74~85` `findById`·`loadResponseWithCurrentJob`, `QualityCase/QualityCaseDetailResponse.java:10` — 중요
  - 역할: 단건 조회에만 검사 결과를 붙이고, 기존 응답 구조는 유지한다
  - LLD: A2·A-2·A-3·R18 / LLD3 Q&A Q7
  - 확인
    1. `@JsonUnwrapped`(`QualityCaseDetailResponse.java:10`) 덕분에 기존 필드가 `qualityCase` 아래로 들어가지 않고 최상위에 남는다 → 기존 `GET /{id}` 사용처의 JSON 경로가 그대로다
    2. 75행의 404가 76행 검사 결과 조회보다 먼저다 → 삭제된 사례의 검사 결과는 조회되지 않는다
    3. 복구(149행)는 `findById`가 아니라 `loadResponseWithCurrentJob`을 부른다 → 복구 응답에 검사 결과가 섞이지 않고 응답 형식이 이전과 같다
    4. 두 조회가 같은 `readOnly` 트랜잭션(73행)이다
  - 잘못되면: 프론트의 기존 `$.status` 같은 경로가 깨지거나, 복구 응답 형식이 달라짐
  - 근거: `InspectionResultControllerTest.사례_단건_조회에_검사_결과_전부가_최신_등록순으로_...`, 기존 `QualityCaseControllerTest.품질사례_단건_조회` / 다이어그램 `23-quality-case-detail.md`

### 4. 비동기 처리·동시성·재시도

- [ ] **4-1** `InspectionResult/InspectionResultService.java:22`, `:43` 행 잠금 없는 조회·삭제 — 낮음
  - 역할: 검사 결과 API는 사례 행을 잠그지 않는다 (`QualityCaseService`의 쓰기는 `getQualityCaseForUpdate`로 잠금)
  - LLD: 요구사항 없음 (after "요구사항 밖에서 추가된 동작")
  - 확인
    1. 요청 스레드 A가 22행에서 사례를 읽고, 요청 스레드 B가 사례의 `deleted_at`을 쓰고 커밋한 뒤, A가 `inspection_result`를 INSERT할 수 있다 → 삭제된 사례에 검사 결과 1건이 붙지만 복구 전까지 조회되지 않는다
    2. 요청 스레드 A와 B가 같은 검사 결과를 함께 읽고 둘 다 43행 DELETE를 실행하면 뒤쪽은 지울 행이 없다
  - 잘못되면: 동시 삭제·수정의 뒤쪽 요청이 404 대신 500으로 응답할 수 있음 (미검증)
  - 근거: 테스트 없음, 수동 확인 필요
  - 열린 질문: 단일 담당자 위주 MVP에서 처리할 필요가 있는지

## 제외한 코드

- `InspectionResult/InspectionResultController.java`: 서비스 호출과 상태 코드 지정만
- `InspectionResult/InspectionResultRequest.java`, `InspectionResultResponse.java`: DTO, 제약은 애노테이션에 그대로 보임
- `InspectionResult/InspectionResult.java` 생성자·`update`: 필드 대입만
- `InspectionResult/InspectionResultNotFoundException.java`, `InvalidInspectionResultException.java`: 기존 패턴 반복
- `findAllByQualityCase_IdOrderByIdDesc`: 조회 전용 쿼리
