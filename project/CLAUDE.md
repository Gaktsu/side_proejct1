# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트

AI 기반 품질사례 지식화 및 원인조사 지원 시스템 (12주 MVP). 자동차 부품 품질 담당자가 신규 품질 문제를 등록하면 과거 `RESOLVED` 사례를 의미 기반으로 검색하고, JEV로 후보를 선별한 뒤 RAG + LLM이 원인 후보·확인사항을 제시한다. **AI는 원인을 확정하지 않는다** — 최종 원인/조치는 담당자가 `/resolve`로 확정한다.

기획서 원본: `/home/minsung/Documents/Backend/개인 프로젝트/첫번째/1주 (기획으로 인해 생략)/기획서/기획서.md` (API 목록, UI 구성, 설계 이유 포함)

현재 상태: 엔티티 7개 + Flyway V1 스키마. API는 Product / ManufacturingProcess / Equipment / QualityCase의 **등록·조회·수정·삭제** 구현됨 (수정은 PATCH로 수정 가능 필드 전체를 받아 교체, 코드·설비 소속 공정은 수정 불가, 목록은 페이징·필터 없음). QualityCase의 `caseCode`는 서버가 `QC-yyyyMMdd-랜덤8자리`로 임시 생성 (추후 가상 사례 생성기에서 재검토). 나머지 엔티티는 Repository/Service/Controller 없음. 새 API 작업 흐름(구현 규칙, 테스트, `diagrams/` 순서도)은 `.claude/skills/domain-api-feature` 스킬을 따른다. 구현 순서는 Product → Process → Equipment → QualityCase(완료) → Qdrant 결합 끊기 → Embedding(Spring 내부 통합) → 가상 품질 사례 생성기로 반복 테스트 → 별도 서비스 분리 여부 판단, 이후 InspectionResult → Analysis → AnalysisCandidate → React → JEV → RAG → LLM. InspectionResult/Analysis/AnalysisCandidate는 Embedding·Qdrant·가상 사례가 있어야 의미가 있어 후순위로 미룸 (2026-10-14 결정). MVP에서 로그인/권한, 비동기 처리는 제외.

Qdrant 연결은 순서를 앞당겨 클라이언트만 구현됨 (`com.iot.project.qdrant`): `QdrantClient`(RestClient로 컬렉션 확인/생성, upsert, search, delete)와 기동 시 컬렉션 확인(`QdrantCollectionInitializer`). 아직 호출하는 곳은 없다 — resolve 시 적재·유사 사례 검색은 Embedding 이후 연결. 기동 시 Qdrant가 꺼져 있거나 컬렉션 설정이 1024/Cosine이 아니면 **앱 전체 기동이 실패**한다. 모노리스 결합 문제를 관찰하려고 의도적으로 유지 중이니 임의로 지연 확인 등으로 바꾸지 않는다. Embedding도 먼저 Spring 안에 통합(모노리스)해 불편함을 확인한 뒤 분리 여부를 판단할 예정.

로컬 Docker: 프로젝트 루트의 `compose.yaml`(mysql:8.0 / qdrant v1.19.1 / quality-app)과 `.env`(DB 비밀번호)는 gitignore 대상이라 저장소에 없다. 컨테이너에는 `application.properties`가 없으므로(`.dockerignore`) 설정은 환경변수로 받는다 — 환경마다 다른 값(`SPRING_DATASOURCE_*`, `QDRANT_URL`)은 compose 환경변수, 어디서나 같은 값은 코드 기본값(`qdrant.collection:quality_case_vectors`). **새 설정 키를 추가하면 컨테이너에서 받을 곳도 함께 정한다** (테스트는 호스트 `application.properties`를 읽어 이 누락을 잡지 못한다).

## 명령어

```bash
./gradlew build                     # 빌드 + 테스트
./gradlew bootRun                   # 실행 (로컬 MySQL + Qdrant localhost:6333 필요)
./gradlew test --tests "com.iot.project.ProjectApplicationTests"   # 단일 테스트 클래스
./gradlew test --tests "*ClassName.methodName"                      # 단일 메서드
```

테스트는 전부 `@SpringBootTest`라 **MySQL과 Qdrant가 둘 다 떠 있어야** 통과한다 (`docker compose up -d qdrant`). `QdrantClientTest`는 `test_quality_case_vectors` 컬렉션을 쓰고 매번 삭제한다. compose의 `quality-app`이 실행 중이면 호스트 8080이 점유되어 `bootRun`이 실패하므로 먼저 `docker compose stop quality-app`.

스택: Java 21, Spring Boot 4.1.1, Spring Data JPA, Validation, Flyway, MySQL, Lombok.

## DB / 스키마 규칙

- `src/main/resources/application.properties`는 `.gitignore`에 포함됨 (로컬 전용). MySQL `localhost:3306/iot_system` 기준.
- `spring.jpa.hibernate.ddl-auto=validate` — Hibernate가 스키마를 만들지 않는다. **엔티티를 바꾸면 반드시 새 Flyway 마이그레이션(`db/migration/V{n}__*.sql`)을 추가**해야 기동된다. 이미 적용된 `V1__init_table.sql`은 수정하지 않는다.
- 기획서의 `Process`는 `java.lang.Process` 충돌 때문에 `ManufacturingProcess` / 테이블 `manufacturing_process`로 구현됨. FK 컬럼명은 `process_id` 유지. API 경로는 기획서대로 `/api/processes`.
- 기획서와 다른 컬럼명: `analysis.target_quality_case_id`, `analysis_candidate.candidate_quality_case_id`.
- 패키지는 도메인별(`com.iot.project.<Entity>`)로 나뉘며 엔티티와 enum이 같은 패키지에 있다. 엔티티는 `@Getter` + `@NoArgsConstructor(access = PROTECTED)`, 연관관계는 `@ManyToOne(fetch = LAZY)`, enum은 `EnumType.STRING`.

## 도메인 규칙 (Service에서 검증)

- `QualityCase.status`: `OPEN` → `RESOLVED`. OPEN일 때 `rootCause/correctiveAction/resultSummary`는 NULL. PATCH/DELETE는 OPEN 사례만 허용.
- `QualityCase.equipment`가 있으면 `equipment.manufacturingProcess`가 `QualityCase.manufacturingProcess`와 같아야 한다.
- `InspectionResult`는 `numericValue`, `textValue` 중 최소 하나 필수.
- `Analysis.analysisStatus`(`ANALYZING/COMPLETED/INSUFFICIENT_EVIDENCE/FAILED`)는 `QualityCase.status`와 별개 개념. `Analysis`는 AI 분석 1회 실행.
- `AnalysisCandidate`는 Qdrant 검색 결과와 JEV 판단(Score/Null/Choice)을 기록해 Qdrant·JEV·LLM 중 어느 단계 문제인지 추적하기 위한 테이블. 현재 Choice 여부 컬럼은 없음.
- MySQL이 원본, Qdrant(`quality_case_vectors`, Point ID = `QualityCase.id`)는 검색용 벡터만 보관. **`RESOLVED` 사례만 Qdrant에 적재**한다 (resolve 시 Embedding 생성 후 추가).

## Embedding 규칙

- 모델: **`telepix/PIXIE-Rune-v1.0`** (dense, Apache-2.0). 선정 근거와 후보 비교는 `quality_retrieval_benchmark_v1/`, `v2/`의 `results/` 참고.
- 벡터 1024차원, 유사도 cosine → Qdrant 컬렉션 `quality_case_vectors`는 `size=1024, distance=Cosine`.
- 신규 문제(검색 쿼리)는 앞에 **`query: ` 접두어**를 붙이고, 과거 사례(문서)는 접두어 없이 임베딩한다.
- 최대 입력 6144 토큰.
- 사례 문서 텍스트는 `공정/제품/증상/관찰/확정 원인/조치` 필드를 같은 형식으로 이어 붙여 만든다. 필드 구성을 바꾸면 기존 벡터를 모두 다시 적재해야 한다.
- 임베딩은 부정 조건("~는 정상", "~는 아니다")을 구분하지 못한다(벤치마크 대조쌍 1/5). Qdrant는 후보를 **top-5 이상** 넉넉히 가져오는 역할만 맡고, 원인 판별은 JEV/LLM 단계에서 한다.
