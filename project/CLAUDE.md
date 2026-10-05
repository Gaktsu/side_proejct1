# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트

AI 기반 품질사례 지식화 및 원인조사 지원 시스템 (12주 MVP). 자동차 부품 품질 담당자가 신규 품질 문제를 등록하면 과거 `RESOLVED` 사례를 의미 기반으로 검색하고, JEV로 후보를 선별한 뒤 RAG + LLM이 원인 후보·확인사항을 제시한다. **AI는 원인을 확정하지 않는다** — 최종 원인/조치는 담당자가 `/resolve`로 확정한다.

기획서 원본: `/home/minsung/Documents/Backend/개인 프로젝트/첫번째/1주 (기획으로 인해 생략)/기획서/기획서.md` (API 목록, UI 구성, 설계 이유 포함)

현재 상태: 엔티티 + Flyway V1 스키마만 존재. Repository/Service/Controller 없음. 구현 순서는 Product → Process → Equipment → QualityCase → InspectionResult → Analysis → AnalysisCandidate, 이후 React → Embedding → Qdrant → JEV → RAG → LLM. MVP에서 로그인/권한, 비동기 처리는 제외.

## 명령어

```bash
./gradlew build                     # 빌드 + 테스트
./gradlew bootRun                   # 실행 (로컬 MySQL 필요)
./gradlew test --tests "com.iot.project.ProjectApplicationTests"   # 단일 테스트 클래스
./gradlew test --tests "*ClassName.methodName"                      # 단일 메서드
```

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
