# LLD 3 검사 결과(InspectionResult) API 요구사항

- 상태: 구현 전 확정본 작성 중 (2026-11-09). 구현 후 점검은 같은 폴더의 `lld3-inspection-result_after.md`
- 출처: 대화 (2026-11-09), `/home/minsung/Documents/Backend/개인 프로젝트/설계/LLD 정합화/LLD3 기반 설계 구체화/`(`LLD3 설계 확정안.md`, `LLD3 Q&A.md`), `docs/lld/LLD_03_AI분석결과_최종판단.md`, `개인 프로젝트/기획서.md` §12·§17

## 범위
- 만드는 것: InspectionResult 등록·수정·물리 삭제 API, 품질사례 단건 조회 응답에 해당 사례의 검사 결과 전부 포함
- 만들지 않는 것: AI 분석 결과 검증·저장과 `COMPLETED` 상태(LLM 모델 미정), SSE, 권한 검증(ADR-0002), 검사 결과 단건·별도 목록 조회 API
- 그대로 두는 것: 담당자 최종 판단은 기존 `POST /api/quality-cases/{id}/resolve`로 충족하며 변경하지 않음

## API
| ID | Method | 경로 | 성공 | 실패 |
|---|---|---|---|---|
| A1 | POST | `/api/quality-cases/{id}/inspection-results` | 201 검사 결과 | 400 입력 오류 / 404 사례 없음·삭제됨 |
| A2 | GET | `/api/quality-cases/{id}` (기존 단건 조회 확장) | 200 사례 + `inspectionResults` (없으면 `[]`) | 404 사례 없음·삭제됨 |
| A3 | PUT | `/api/inspection-results/{id}` | 200 검사 결과 | 400 입력 오류 / 404 검사 결과 없음·사례 삭제됨 |
| A4 | DELETE | `/api/inspection-results/{id}` | 204 | 404 검사 결과 없음·사례 삭제됨 |

- [ ] A-1. 응답 필드: `id`, `qualityCaseId`, `inspectionItem`, `inspectionMethod`, `numericValue`, `textValue`, `unit`, `specMin`, `specMax`, `specText`, `resultStatus`, `measuredAt`, `createdAt`
- [ ] A-2. A2 응답은 기존 `QualityCaseResponse` 필드를 모두 유지하고 `inspectionResults` 배열을 추가한다. 배열 항목은 A-1과 같은 필드다
- [ ] A-3. 품질사례 목록·등록·수정·해결·해결 취소·복구와 유사 사례 검색의 응답에는 `inspectionResults`를 넣지 않는다

## 도메인 규칙

사례 상태

- [ ] R1. 없거나 논리 삭제된 사례에 A1·A2를 호출하면 404
- [ ] R2. 없는 검사 결과 id로 A3·A4를 호출하면 404
- [ ] R3. 검사 결과가 속한 사례가 논리 삭제됐으면 A3·A4는 404
- [ ] R4. 사례가 OPEN이든 RESOLVED든 A1·A3·A4가 성공한다 (상태 때문에 409를 내지 않음)
- [ ] R5. 사례의 논리 삭제·복구·해결·해결 취소는 검사 결과를 바꾸지 않고, 복구하면 A2로 다시 조회된다

입력 검증 (A1·A3 공통)

- [ ] R6. `numericValue`가 null이고 `textValue`가 null·공백이면 400이고 저장되지 않는다
- [ ] R7. `numericValue`와 `textValue`가 둘 다 있어도 저장된다
- [ ] R8. `specMin`과 `specMax`가 둘 다 있고 `specMin > specMax`이면 400. 같으면 허용, 한쪽만 있으면 이 검사를 하지 않는다
- [ ] R9. 측정값이 규격 범위 밖이어도 거절하지 않고 저장한다
- [ ] R10. 서버는 `resultStatus`를 계산하거나 바꾸지 않고 입력값을 저장한다 (범위 밖 값 + PASS 조합도 저장)
- [ ] R11. `inspectionItem` 필수·공백 불가·100자 이하, `inspectionMethod` 100자 이하, `textValue`·`specText` 255자 이하, `unit` 30자 이하. 위반 시 400
- [ ] R12. `resultStatus`는 PASS/FAIL 중 하나로 필수. 누락이나 다른 값이면 400
- [ ] R13. `numericValue`·`specMin`·`specMax`가 정수부 12자리 또는 소수부 6자리를 넘으면 400
- [ ] R14. `measuredAt`은 선택 입력이다

수정·삭제·목록

- [ ] R15. A3은 수정 가능 필드 전체를 교체한다. 보내지 않은 선택 필드는 NULL이 되고, `id`·소속 사례·`createdAt`은 바뀌지 않는다
- [ ] R16. A4는 행을 물리 삭제한다. 이후 A2에 나오지 않고 같은 id의 A3·A4는 404
- [ ] R17. 한 사례에 여러 건을 등록할 수 있고, 새 등록이 이전 결과를 지우거나 교체하지 않는다
- [ ] R18. A2의 `inspectionResults`는 해당 사례의 검사 결과 전부를 최신 등록순(`id` 내림차순)으로 담는다

임베딩·LLM과의 분리

- [ ] R19. A1·A3·A4는 `QualityCase`의 상태·`source_version`·수정 시각을 바꾸지 않고, `indexing_job`·`search_visibility_job`·`quality_case_revision`을 만들지 않는다
- [ ] R20. 임베딩 텍스트(`QualityCaseDocument.toText`·`toQueryText`)에 검사 결과를 넣지 않고, 검사 결과 API는 `EmbeddingClient`·`QdrantClient`를 호출하지 않는다
- [ ] R21. `AnalysisWorker`는 검사 결과를 읽지 않으며, 진행 중인 분석이 있어도 A1·A3·A4는 성공한다

## 데이터·스키마
- [ ] D1. 새 Flyway 마이그레이션 없음. V1의 `inspection_result` 컬럼과 엔티티 컬럼 매핑을 그대로 쓴다 (`updated_at` 추가 안 함)

## 미정·나중에
- LLM 단계에서 검사 결과를 입력으로 쓰려면, 값이 바뀔 때 재분석을 어떻게 할지 먼저 정한다
- LLM 출력 스키마·프롬프트, AI 결과 저장 트랜잭션과 `COMPLETED` 상태는 LLM 모델을 정한 뒤 설계한다
- 권한 검증은 ADR-0002 도입 때 다룬다

## 주의
- 기획서 §17의 AI 분석 흐름에는 `InspectionResult 조회 → 검색용 Text 구성`이 있지만 이번 결정으로 적용하지 않는다. 임베딩·LLM 어디에도 넣지 않는다
- `InspectionResult`는 담당자가 입력하는 측정 기록이다. LLM이 제안하는 추가 확인사항은 `Analysis.aiCheckItems`이고 서로 다른 데이터다
- 해결 후 수정·삭제를 막는 안(RESOLVED는 409)은 검토 뒤 채택하지 않았다
- 품질사례와 검사 결과는 1대N이다. 검사 결과는 사례가 필수이고, 사례는 검사 결과가 0건일 수 있다
- 별도 목록 API(`GET /api/quality-cases/{id}/inspection-results`)는 단건 조회 포함 방식으로 바꾸면서 뺐다
