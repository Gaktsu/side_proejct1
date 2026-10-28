# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트

AI 기반 품질사례 지식화 및 원인조사 지원 시스템 (12주 MVP). 자동차 부품 품질 담당자가 신규 품질 문제를 등록하면 과거 `RESOLVED` 사례를 의미 기반으로 검색하고, JEV로 후보를 선별한 뒤 RAG + LLM이 원인 후보·확인사항을 제시한다. **AI는 원인을 확정하지 않는다** — 최종 원인/조치는 담당자가 `/resolve`로 확정한다.

기획서 원본: `/home/minsung/Documents/Backend/개인 프로젝트/내가 작성한 기술서/1주 (기획으로 인해 생략)/기획서/기획서.md` (API 목록, UI 구성, 설계 이유 포함)

현재 상태: 엔티티 7개 + Flyway V1 스키마. API는 Product / ManufacturingProcess / Equipment / QualityCase의 **등록·조회·수정·삭제** 구현됨, QualityCase는 `POST /{id}/resolve`(해결 확정 + 문서 텍스트 임베딩 + Qdrant upsert, 실패 시 503 롤백)와 `GET /{id}/similar-cases`(OPEN 사례의 쿼리 텍스트 임베딩 → Qdrant top-10 → MySQL에서 RESOLVED만 남기고 Qdrant 순서·score 유지, 실패 시 503) 포함 (수정은 PATCH로 수정 가능 필드 전체를 받아 교체, 코드·설비 소속 공정은 수정 불가, 목록은 페이징·필터 없음). QualityCase의 `caseCode`는 서버가 `QC-yyyyMMdd-랜덤8자리`로 임시 생성 (추후 가상 사례 생성기에서 재검토). 나머지 엔티티는 Repository/Service/Controller 없음. 새 API 작업 흐름(구현 규칙, 테스트, `diagrams/` 순서도)은 `.claude/skills/domain-api-feature` 스킬을 따른다. 구현 순서는 Product → Process → Equipment → QualityCase → Qdrant 결합 끊기(완료) → Embedding(Spring 내부 통합, 완료) → resolve API + 적재(완료) → 유사 사례 검색 API(완료, RESOLVED 필터는 upsert 후 커밋 실패로 남은 벡터를 거르기 위함) → **가상 품질 사례 생성기로 반복 테스트(메모리·응답 시간·다른 API 영향 측정)** → 별도 서비스 분리 여부 판단, 이후 InspectionResult → Analysis → AnalysisCandidate → React → JEV → RAG → LLM. InspectionResult/Analysis/AnalysisCandidate는 Embedding·Qdrant·가상 사례가 있어야 의미가 있어 후순위로 미룸 (2026-10-14 결정). 생성기 전에 파이프라인을 먼저 잇는 이유: Embedding·Qdrant 클라이언트에 호출처가 없으면 가상 사례가 MySQL에만 쌓여 측정할 것이 없음 (2026-10-15 결정). 문서 텍스트 매핑은 `QualityCaseDocument`(증상 = `defectType - title`, 관찰 = `problemDescription`, 설비 있으면 `설비:` 줄, `resultSummary`·LOT·수량·날짜 제외), 검색 쿼리 텍스트는 같은 형식에서 `확정 원인`·`조치`를 뺀 `toQueryText`(OPEN 사례엔 아직 없으므로, 저장하지 않아 바꿔도 재적재 불필요). 유사 사례 검색 설계·Q&A는 기술서 `5주차/유사 사례 검색/`. resolve 설계 판단과 Q&A(트랜잭션 밖 임베딩 등 나중 선택지)는 기술서 `/home/minsung/Documents/Backend/개인 프로젝트/내가 작성한 기술서/4주차/Spring->임베딩->qdrant 연결/`(`흐름 설계.md`, `흐름 Q&A.md`)에 있고, 설계를 바꾸면 그 문서들도 고친다. MVP에서 로그인/권한, 비동기 처리는 제외.

**LLD 정합화 진행 중 (2026-10-27~)**: 저장소 `docs/`(FR/NFR, HLD, ADR, LLD 1~4, review)가 최신 설계이고, 위·아래 서술과 충돌하면 docs와 아래 확정 사항이 우선한다. 기존 코드는 유지하며 충돌 부분만 고치고, LLD의 큰 틀을 바꿔야 하면 사용자에게 먼저 말한다. LLD 항목을 하나씩 확정할 때마다 이 문단을 갱신한다. 진행 순서: **LLD 1(구현됨, `feature/lld1-indexing`)** → LLD 4 → LLD 2(검색·후보까지, JEV/LLM 제외) → InspectionResult. 가상 사례 생성기는 그 뒤로 밀림.
- 확정: 상태값 `OPEN` 유지(LLD의 OPENED 아님) / 삭제된 사례 조회는 404 / `/similar-cases`는 유지하되 디버그·성능 측정용 / JEV·LLM은 당분간 제외 / Analysis 상태 enum은 미정
- LLD 1 세부 설계(구현됨 — 이 문단이 아래 "현재 상태"의 resolve 503 롤백·RESOLVED 수정 불가 서술을 대체): `/home/minsung/Documents/Backend/개인 프로젝트/설계/LLD 정합화/LLD1 기반 설계 구체화/`(`LLD1 설계 확정안.md`, `LLD1 Q&A.md`). 요지 — resolve는 한 트랜잭션에서 RESOLVED + `source_version`+1 + `indexing_job`(embedding PENDING / qdrant WAITING) 기록 후 200, 임베딩·upsert는 단일 스레드 처리기가 트랜잭션 밖에서(재시도 최초 포함 5회·메모리 카운트, 기동 시 PROCESSING→PENDING). RESOLVED 수정은 상태 그대로 허용하고 직전 값은 `quality_case_revision`에 남김(진행 중 job이 있어도 허용, 처리기가 `job.source_version`≠`사례.source_version`이면 SUPERSEDED로 건너뜀 — LLD 1 3-C.2 잠정 정책 변경). 1024 토큰 검사는 resolve·수정 트랜잭션 안에서 동기. 벡터는 MySQL에 보존하지 않고 재임베딩. Qdrant payload `sourceVersion`·`searchable`, 검색은 `searchable=true` 필터 + MySQL 버전 대조. 코드: `com.iot.project.IndexingJob`(`IndexingJob`, `IndexingWorker` — `@Scheduled`가 아닌 자체 단일 스레드 `ScheduledExecutorService`), `QualityCaseRevision`, `GET /{id}/revisions`, Flyway V2. 설정 키 `indexing.worker.enabled`(기본 true)·`indexing.poll-delay-ms`(5000)·`indexing.retry-delays-seconds`(30,60,120,240)는 코드 기본값이라 compose 수정 불필요. 테스트는 `src/test/resources/config/application.properties`에서 처리기를 끄고 재시도를 비워 `pollOnce()`를 직접 호출한다
- 로그인·역할: `docs/architecture/ADR/0002-login-role-approval.md`(제안) — MEMBER/APPROVER, 팀원은 최종 담당자에게 승인 요청. 가상 권한도 지금은 넣지 않고 프론트를 붙이거나 승인 요청 흐름을 만들 때 도입 (2026-10-27). 아래 "MVP에서 로그인/권한, 비동기 처리는 제외"는 이 ADR과 LLD 1로 대체될 예정
- 설계 문서는 `개인 프로젝트/설계/LLD 정합화/` 아래 새 폴더에 쓰고, 기존 기술서 문서(4·5주차)는 고치지 않는다

Qdrant 연결은 순서를 앞당겨 클라이언트만 구현됨 (`com.iot.project.qdrant`): `QdrantClient`(RestClient로 컬렉션 확인/생성, upsert, search, delete). 호출처는 resolve(upsert)와 유사 사례 검색(search). 기동 시에는 Qdrant를 호출하지 않고, upsert/search/delete 첫 호출 때 컬렉션을 확인한다(`ensureReady`, 실패 시 다음 호출에서 재확인). Qdrant가 꺼져 있거나 컬렉션 설정이 1024/Cosine이 아니면 **해당 Qdrant 호출만 실패**하고 앱과 다른 API는 정상 동작한다 (2026-10-15 결합 끊기, 이전엔 기동 실패였음). Embedding도 먼저 Spring 안에 통합(모노리스)해 불편함을 확인한 뒤 분리 여부를 판단할 예정.

Embedding은 `com.iot.project.embedding.EmbeddingClient`(`embedDocument` / `embedQuery`)로 구현됨, 호출처는 resolve(`embedDocument`)와 유사 사례 검색(`embedQuery`). PIXIE-Rune을 ONNX Runtime(`onnxruntime` 1.31.0) + DJL `tokenizers` 0.38.0으로 JVM 안에서 실행한다. 모델 파일(`model.onnx`, `model.onnx_data` 2.27GB, `tokenizer.json`)은 git·이미지에 넣지 않고 `~/models/pixie-rune-onnx/`에 두며 `scripts/export_pixie_onnx.py`로 만든다(ONNX 출력이 CLS + L2 정규화된 최종 벡터라 Java는 그대로 반환). 경로는 `embedding.model-dir`(컨테이너는 `EMBEDDING_MODEL_DIR` + bind mount `/home/minsung/models/pixie-rune-onnx:/models/pixie-rune:ro` — docker를 sudo로 실행하면 `~`가 `/root`가 되어 빈 폴더가 연결되므로 절대 경로), 코드 기본값 없음. **첫 호출 때 모델을 로딩**(약 7초, RSS +1.7GB 대부분 heap 밖 native → `-Xmx`로 제한 안 됨)하고, 경로가 없거나 로딩에 실패하면 그 호출만 실패하고 다음 호출에서 다시 시도한다. 실제 모델을 쓰는 자동 테스트는 없다(느리고 2.27GB 필요). **`onnxruntime`/`tokenizers` 버전을 올리거나 모델을 다시 변환하면 sentence-transformers 기준 벡터와 비교(cos > 0.9999)를 다시 한다** — Python 쪽은 변환 스크립트가 자동 검증하고, Java 쪽은 `EmbeddingClient`로 같은 텍스트를 임베딩해 비교한다 (2026-10-15 검증: 최소 cos 0.99999949).

로컬 Docker: 프로젝트 루트의 `compose.yaml`(mysql:8.0 / qdrant v1.19.1 / quality-app)과 `.env`(DB 비밀번호)는 gitignore 대상이라 저장소에 없다. 컨테이너에는 `application.properties`가 없으므로(`.dockerignore`) 설정은 환경변수로 받는다 — 환경마다 다른 값(`SPRING_DATASOURCE_*`, `QDRANT_URL`, `EMBEDDING_MODEL_DIR`)은 compose 환경변수, 어디서나 같은 값은 코드 기본값(`qdrant.collection:quality_case_vectors`). **새 설정 키를 추가하면 컨테이너에서 받을 곳도 함께 정한다** (테스트는 호스트 `application.properties`를 읽어 이 누락을 잡지 못한다).

## 명령어

```bash
./gradlew build                     # 빌드 + 테스트
./gradlew bootRun                   # 실행 (로컬 MySQL 필요, Qdrant 기능은 localhost:6333)
./gradlew test --tests "com.iot.project.ProjectApplicationTests"   # 단일 테스트 클래스
./gradlew test --tests "*ClassName.methodName"                      # 단일 메서드
```

테스트는 전부 `@SpringBootTest`라 **MySQL이 떠 있어야** 통과하고, `QdrantClientTest`만 Qdrant도 필요하다 (`docker compose up -d qdrant`). `QdrantDownTest`는 닫힌 포트(`localhost:1`)로 Qdrant 꺼짐 상황을, `EmbeddingModelMissingTest`는 없는 경로로 모델 파일 없음 상황을 재현한다. `QdrantClientTest`는 `test_quality_case_vectors` 컬렉션을 쓰고 매번 삭제한다. compose의 `quality-app`이 실행 중이면 호스트 8080이 점유되어 `bootRun`이 실패하므로 먼저 `docker compose stop quality-app`.

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
- 최대 입력 **1024 토큰** (모델 자체는 6144). 넘으면 자르지 않고 `EmbeddingInputTooLongException`(400)으로 거절하며, resolve는 롤백되어 OPEN으로 남는다. 이유: attention 비용이 토큰 수의 제곱으로 늘고 ONNX Runtime이 늘어난 메모리를 반환하지 않음 (2026-10-16 측정: 1133토큰 8초/+300MB, 2145토큰 21.6초/+1.2GB, 6144토큰은 15GB 노트북에서 OOM으로 프로세스 종료). DJL 토크나이저는 truncation을 켜면 `tokenizer_config.json`이 없다는 이유로 상한을 512로 바꿔 경고만 남기고 자르므로 `optTruncation(false)`로 전체 토큰을 센 뒤 직접 검사한다. 상한 이하에서는 Python과 같은 벡터임을 확인 (627토큰 cos 1.0000002).
- 사례 문서 텍스트는 `공정/제품/증상/관찰/확정 원인/조치` 필드를 같은 형식으로 이어 붙여 만든다. 필드 구성을 바꾸면 기존 벡터를 모두 다시 적재해야 한다.
- 임베딩은 부정 조건("~는 정상", "~는 아니다")을 구분하지 못한다(벤치마크 대조쌍 1/5). Qdrant는 후보를 **top-5 이상** 넉넉히 가져오는 역할만 맡고, 원인 판별은 JEV/LLM 단계에서 한다.
