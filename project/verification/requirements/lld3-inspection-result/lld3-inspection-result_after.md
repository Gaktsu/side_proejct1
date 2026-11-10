# LLD 3 검사 결과(InspectionResult) API 요구사항 점검 (구현 후)

- 점검: 2026-11-09, 브랜치 `feature/lld3-inspection-result` (미커밋)
- 기준: `lld3-inspection-result_before.md`
- 테스트: ./gradlew cleanTest test — tests 152 / failures 13 / errors 0 (점검 시점 실행). 실패 13개는 전부 `QdrantClientTest`로 구현 전 기준선과 같다 (Qdrant 연결 안 됨). `InspectionResultControllerTest` 19개 통과
- 경로 앞부분 `src/main/java/com/iot/project/` 생략, 행 번호는 점검 시점 기준. `IRS` = `InspectionResult/InspectionResultService.java`, `QCS` = `QualityCase/QualityCaseService.java`, `IRT` = `InspectionResultControllerTest`

## 요약

| 판정 | 개수 | 항목 |
|---|---|---|
| 부합 | 29 | A1~A4, A-1~A-3, R1~R21, D1 |
| 부합(수동 확인 필요) | 0 | |
| 변경 구현 | 0 | |
| 미부합 | 0 | |
| 확인 불가 | 0 | |

## API

| ID | 요구사항 | 로직 위치 | 판정 | 근거·이유 |
|---|---|---|---|---|
| A1 | 등록 201 / 400 / 404 | `InspectionResult/InspectionResultController.java:15~18`, `IRS:20~30` | 부합 | `IRT.등록_201_입력값과_사례id_반환`, `등록_없는_사례_404` |
| A2 | 사례 단건 조회 200 + `inspectionResults` / 404 | `QualityCase/QualityCaseController.java:29`, `QCS:74~78` | 부합 | `IRT.사례_단건_조회에_...`, `검사_결과가_없는_사례의_단건_조회는_빈_배열` |
| A3 | 수정 200 / 400 / 404 | `InspectionResultController.java:22~23`, `IRS:32~40` | 부합 | `IRT.수정은_전체_교체라_...`, `수정_삭제_없는_검사_결과_404` |
| A4 | 삭제 204 / 404 | `InspectionResultController.java:27`, `IRS:42~44` | 부합 | `IRT.삭제하면_행이_없어지고_...` |
| A-1 | 응답 13개 필드 | `InspectionResult/InspectionResultResponse.java` | 부합 | record 필드와 일치, `IRT.등록_201_...` |
| A-2 | 기존 사례 필드 유지 + `inspectionResults` 배열 | `QualityCase/QualityCaseDetailResponse.java:10~11` (`@JsonUnwrapped`) | 부합 | `IRT.사례_단건_조회에_...`(`$.id`·`$.caseCode`가 최상위, `$.qualityCase` 없음), 기존 `품질사례_단건_조회` 통과 |
| A-3 | 다른 사례 API 응답에는 없음 | `QCS:81`(복구도 `QualityCaseResponse`), `QualityCaseController.java`의 나머지 반환형 | 부합 | 반환형이 `QualityCaseResponse`, 목록은 `IRT.사례_목록_응답에는_검사_결과가_없음`. 유사 사례 검색도 `QualityCaseResponse` 사용 |

## 도메인 규칙

| ID | 요구사항 | 로직 위치 | 판정 | 근거·이유 |
|---|---|---|---|---|
| R1 | 없거나 삭제된 사례 A1·A2 404 | `IRS:22~23`, `QualityCase/QualityCaseRepository.java:34`, `QCS:81~85` | 부합 | `IRT.등록_없는_사례_404`, `등록_논리_삭제된_사례_404`, `사례_논리_삭제_후_복구하면_...`(삭제 중 404) |
| R2 | 없는 검사 결과 A3·A4 404 | `IRS:59~60` | 부합 | `IRT.수정_삭제_없는_검사_결과_404` |
| R3 | 사례가 삭제됐으면 A3·A4 404 | `InspectionResult/InspectionResultRepository.java:14` | 부합 | `IRT.소속_사례가_논리_삭제되면_...` |
| R4 | OPEN·RESOLVED 모두 A1·A3·A4 성공 | `IRS` 전체에 사례 상태 분기 없음 | 부합 | `IRT.RESOLVED_사례도_등록_수정_삭제_가능하고_...` |
| R5 | 사례 삭제·복구·해결·해결 취소가 검사 결과를 바꾸지 않음 | `QCS`에서 `inspectionResultRepository` 사용은 `:76` 조회뿐 | 부합 | 삭제·복구는 `IRT.사례_논리_삭제_후_복구하면_...`, 해결·해결 취소는 코드 확인(전용 테스트 없음) |
| R6 | 숫자값 없고 문자값 null·공백이면 400 | `IRS:48` | 부합 | `IRT.측정값이_숫자_문자_모두_없거나_공백이면_400_저장_안_됨` |
| R7 | 둘 다 있어도 저장 | `IRS:48` (둘 다 있는 경우를 막지 않음) | 부합 | `IRT.숫자_문자_측정값을_둘_다_보내면_저장` |
| R8 | `specMin > specMax`만 400 | `IRS:51~52` | 부합 | `IRT.규격_최소값이_최대값보다_크면_400_...` |
| R9 | 규격 밖 측정값도 저장 | `IRS:47~56` (측정값과 규격 비교 없음) | 부합 | `IRT.규격_밖_측정값도_...` |
| R10 | `resultStatus` 입력값 그대로 | `IRS:24~27`, `:36~38` | 부합 | `IRT.규격_밖_측정값도_...`(범위 밖 + PASS 저장) |
| R11 | 필수·길이 제한 400 | `InspectionResult/InspectionResultRequest.java:14~21`, 컨트롤러 `@Valid` | 부합 | `IRT.필수값_길이_자릿수_상태값이_잘못되면_400` (`inspectionMethod`·`specText`는 애노테이션 확인) |
| R12 | `resultStatus` 필수, PASS/FAIL만 | `InspectionResultRequest.java:22`, enum 역직렬화 실패 400 | 부합 | 같은 테스트 |
| R13 | 정수 12·소수 6자리 초과 400 | `InspectionResultRequest.java:16`, `:19~20` | 부합 | 같은 테스트 (`numericValue`로 확인, `specMin`·`specMax`는 애노테이션 확인) |
| R14 | `measuredAt` 선택 | `InspectionResultRequest.java:23` (제약 없음) | 부합 | `IRT.수정은_전체_교체라_...` |
| R15 | 전체 교체, `id`·사례·`createdAt` 불변 | `InspectionResult/InspectionResult.java:84~` (사례·`createdAt` 미포함) | 부합 | `IRT.수정은_전체_교체라_...` |
| R16 | 물리 삭제, 이후 조회 안 되고 404 | `IRS:43` | 부합 | `IRT.삭제하면_행이_없어지고_...` |
| R17 | 여러 건 등록, 교체 없음 | `IRS:24~29` (INSERT만) | 부합 | `IRT.사례_단건_조회에_...` (2건) |
| R18 | 전부를 최신 등록순으로 | `InspectionResultRepository.java:11`, `QCS:76` | 부합 | `IRT.사례_단건_조회에_...`(순서 단언) |
| R19 | 사례 상태·버전·수정 시각 불변, job·revision 안 만듦 | `IRS`에 해당 코드 없음 | 부합 | `IRT.RESOLVED_사례도_...`(버전·상태·`updated_at`·3개 테이블 행 수 단언) |
| R20 | 임베딩 텍스트 미포함, 임베딩·Qdrant 호출 없음 | `QualityCase/QualityCaseDocument.java`, `IRS` import | 부합 | `Inspection` 참조 0건, `IRS` 의존성은 저장소 2개뿐 |
| R21 | `AnalysisWorker`가 읽지 않음, 진행 중 분석과 무관 | `Analysis/` 패키지 `Inspection` 참조 0건 | 부합 | `IRT.진행_중인_분석이_있어도_등록_가능` |

## 데이터·스키마

| ID | 요구사항 | 로직 위치 | 판정 | 근거·이유 |
|---|---|---|---|---|
| D1 | 새 Flyway 없음, 컬럼 매핑 그대로 | `InspectionResult/InspectionResult.java` 필드 변경 없음 | 부합 | 변경 파일에 마이그레이션 없음, `ddl-auto=validate`로 기동 통과 |

## 변경 구현·미부합 상세
- 해당 없음

## 요구사항 밖에서 추가된 동작
- `QualityCaseService.restore`가 공통 조회를 `findById` 대신 `loadResponseWithCurrentJob`(`QCS:81`, `:149`)으로 호출한다. `findById` 응답이 바뀌어 분리했고 복구 응답은 이전과 같다
- 사례 단건 조회의 SELECT가 검사 결과 조회 1건만큼 늘어난다
- 입력 오류(400)와 사례·검사 결과 없음(404)이 겹치면 400이 먼저다 (`IRS:21`, `:33`). `QualityCaseService.create`의 기존 순서와 같다
- 사례 행 잠금을 쓰지 않아, 사례 논리 삭제와 동시에 들어온 등록은 삭제 직후 사례에 저장될 수 있다. 저장돼도 사례 복구 전까지 조회되지 않는다
- 같은 검사 결과를 동시에 삭제하거나 수정·삭제가 겹치면 뒤쪽 요청이 500이 될 가능성이 있다 (미검증, 동시성 테스트 없음)

## 미정·나중에 항목 확인
- LLM 입력 연결, AI 결과 저장·`COMPLETED`, 권한 검증은 구현되지 않았다

## 사용자 확인 필요
- 수동 확인 대상 없음. Postman으로 흐름을 직접 볼 경우 순서도 20~23 기준으로 확인한다
- `QdrantClientTest` 13개는 Qdrant를 켠 환경에서 다시 돌려야 통과 여부를 알 수 있다. 이번 변경은 Qdrant 코드를 건드리지 않았다
- 동시 삭제 경합의 500 가능성을 대응할지 (단일 담당자 위주 MVP라면 그대로 두어도 된다)
