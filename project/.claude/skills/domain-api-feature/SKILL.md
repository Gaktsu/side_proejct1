---
name: domain-api-feature
description: 이 프로젝트(품질사례 지식화 시스템, Spring Boot + MySQL)에서 도메인별 REST API 기능(등록/조회/수정/삭제/resolve 등)을 구현하고, 실제 MySQL로 JUnit 테스트를 돌린 뒤, 비즈니스 로직 분기를 Mermaid 순서도로 diagrams/ 에 저장하는 작업 흐름. 사용자가 "QualityCase API 만들어줘", "InspectionResult 등록/삭제 추가", "Controller/Service/DTO/Exception 만들어", "API 만들고 순서도로 그려줘", "로직 시각화", "mermaid flowchart" 등을 말하면 반드시 이 스킬을 사용한다. 기능 구현만 또는 순서도만 요청해도 해당 단계를 이 스킬 규칙대로 수행한다.
---

# 도메인 API 기능 구현 + Mermaid 순서도

이 레포에서 엔티티 하나씩 API를 붙여 나가는 작업의 표준 흐름이다. Product / ManufacturingProcess / Equipment의 등록·삭제가 이 방식으로 만들어졌으니, 새 기능을 만들 때 그 코드를 먼저 읽고 같은 모양으로 맞춘다.

## 0. 범위 확인

- `CLAUDE.md`와 기획서(경로는 CLAUDE.md에 있음)에서 해당 도메인의 API 경로, 도메인 규칙을 확인한다.
- **요청된 동작만** 만든다. "생성, 제거만"이라고 하면 조회/수정은 만들지 않는다. 사용자가 단계적으로 쌓아 가는 프로젝트라 앞질러 만들면 리뷰 부담만 늘어난다.
- 경로가 기획서에 없거나 해석이 갈리면(예: 하위 리소스 생성 경로) 기획서의 기존 경로와 일관되게 고르고, 고른 이유를 작업 시작 전에 한 줄로 알린다.

## 1. 기준선

구현 전에 `./gradlew test`를 한 번 돌려 기존 테스트가 통과하는지 본다. 로컬 MySQL(`localhost:3306/iot_system`)이 떠 있어야 한다. 실패하면 구현 전에 원인을 먼저 보고한다 — 내 변경 때문인지 원래 깨져 있었는지 구분하기 위해서다.

## 2. 구현 규칙

파일은 도메인 패키지(`com.iot.project.<Entity>`) 안에 **하위 폴더 없이** 둔다.

| 파일 | 규칙 |
|---|---|
| `XxxRepository` | `JpaRepository`. 중복 검사용 `existsByXxxCode` 등 필요한 메서드만 |
| `XxxCreateRequest` 등 DTO | Java `record`. Bean Validation(`@NotBlank`, `@Size(max = 컬럼 길이)`). 단순 변환이면 `toEntity()` |
| `XxxResponse` | `record` + `static from(entity)`. 연관 엔티티는 객체 대신 id로 노출 (`processId`) |
| 예외 | `ResponseStatusException` 상속, 생성자에서 상태 코드 + 한국어 메시지 (`"제품을 찾을 수 없습니다. id=" + id`) |
| `XxxService` | `@Service @RequiredArgsConstructor`, 쓰기 메서드에 `@Transactional` |
| `XxxController` | `@RestController`, 생성 `@ResponseStatus(CREATED)`, 삭제 `NO_CONTENT`, `@Valid @RequestBody` |

- 실패 응답 JSON은 `common/GlobalExceptionHandler`(빈 `ResponseEntityExceptionHandler`)가 ProblemDetail로 만든다. 새 핸들러를 만들지 말고 예외 클래스만 추가한다.
- 엔티티는 setter 없이 **생성용 public 생성자만** 추가하고 `createdAt`(`updatedAt`)은 생성자에서 `LocalDateTime.now()`. 상태 변경이 필요하면 의미 있는 메서드(`resolve(...)`)로 만든다.
- 엔티티 필드를 바꾸면 새 Flyway 마이그레이션이 필요하다 (`ddl-auto=validate`).

### 비즈니스 판단 패턴

- **부모 존재 확인**: `findById(...).orElseThrow(() -> new XxxNotFoundException(id))` → 404
- **코드 중복**: `existsByXxxCode` → 409. 이어서 `saveAndFlush`를 `try`로 감싸 `DataIntegrityViolationException`도 Duplicate 예외(409)로 변환한다. exists 검사는 일반적인 경우에 깔끔한 응답을 주고, catch는 동시 요청이 exists를 함께 통과한 경우를 DB UNIQUE 제약으로 막는다.
- **삭제**: `findById` → 404, 그다음 `delete` + `flush()`를 `try`로 감싸 `DataIntegrityViolationException`을 `XxxInUseException`(409)으로 변환. flush는 FK 위반을 커밋 시점이 아니라 이 자리에서 터뜨리기 위해 필요하다. 예외를 다시 던지므로 트랜잭션은 롤백된다.
- 도메인 규칙(CLAUDE.md "도메인 규칙")은 Service에서 검증한다. 예: 설비의 공정 ≠ 사례의 공정 → 400/409.

UNIQUE 컬럼이 있는 등록 API는 3장의 동시성 테스트로 위 처리를 확인한다.

## 3. 테스트

`src/test/java/com/iot/project/<Entity>/XxxControllerTest.java`. 실제 MySQL에 대해 전체 스택을 검증한다 (Postman 검증은 사용자가 따로 한다).

```java
@SpringBootTest
@AutoConfigureMockMvc   // Boot 4: org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Transactional          // 테스트마다 롤백 → 개발 DB에 흔적 없음
class XxxControllerTest {
```

- 요청 body는 텍스트 블록 JSON, 응답 id는 `com.jayway.jsonpath.JsonPath.read(json, "$.id")`.
- 테스트 데이터 코드는 `TEST-` 접두사 (`TEST-P-001`). 개발 DB의 실제 데이터와 안 겹치게.
- 테스트 메서드 이름은 한국어 (`제품코드_중복시_409`). 각 결과 분기(성공, 404, 409, 400)를 하나씩.
- "사용 중" 409를 만들 참조 행은, 아직 API가 없는 테이블이면 `JdbcTemplate` INSERT로 넣는다.
- 같은 테스트 트랜잭션 안에서 만든 엔티티가 삭제 대상을 참조하면 Hibernate가 DB에 가기 전에 `TransientPropertyValueException`을 던진다. 실제 요청은 별도 영속성 컨텍스트이므로, 테스트에서 `entityManager.flush(); entityManager.clear();`로 맞춘다 (코드를 고칠 문제가 아니다).

### 동시성 테스트

UNIQUE 컬럼이 있는 등록 API는 같은 코드로 동시에 요청했을 때 결과가 **201 하나 + 나머지 전부 409**(500 없음)인지 JUnit으로 확인한다. 파일은 `XxxConcurrencyTest`로 따로 둔다.

- **`@Transactional`을 붙이지 않는다.** 각 요청이 자기 트랜잭션을 커밋해야 경쟁이 실제로 일어난다. 테스트 트랜잭션 하나로 묶으면 동시성이 사라진다.
- 그 대신 `@AfterEach`에서 `JdbcTemplate`으로 `TEST-` 행을 직접 지운다. 자식 테이블부터 지워야 FK에 걸리지 않는다.
- 스레드 10개 정도를 `ExecutorService`로 띄운다. `CountDownLatch`로 출발 시점을 맞춰 같은 body를 동시에 보내고, 상태 코드를 모아 개수를 검증한다.
- 경쟁은 확률적이다. catch를 넣기 전 코드에서 먼저 실패하는지 확인하면 테스트가 실제로 문제를 잡는지 알 수 있다. Product/Process/Equipment에서는 10스레드로 3회 모두 재현됐다. MockMvc에서는 잡히지 않은 예외가 500 응답이 아니라 `ServletException`으로 던져진다.
- 반복 실행할 때는 `./gradlew cleanTest test --tests "*ConcurrencyTest"`로 돌린다. `cleanTest`가 없으면 Gradle이 이전 결과를 재사용해서 테스트를 실제로 다시 돌리지 않는다.
- 공용 헬퍼 `src/test/java/com/iot/project/common/ConcurrentRequests.run(threads, request)`를 쓴다.

실행 후 확인:
1. `./gradlew test` → `build/test-results/test/*.xml`에서 tests/failures 개수
2. `mysql`로 `SELECT COUNT(*) FROM <table> WHERE <code> LIKE 'TEST-%'` → 0 (롤백 확인). 계정은 `application.properties`에서 읽는다 — 이 파일은 gitignore 대상이라 스킬에 비밀번호를 적지 않는다.

실패가 있으면 원인을 밝히고 고친 뒤 다시 돌린다. 몇 개가 처음에 실패했고 왜였는지도 보고한다.

## 4. Mermaid 순서도

구현한 로직을 사용자가 한눈에 검증할 수 있게 `diagrams/`(프로젝트 루트)에 저장한다. 사용자가 원하는 것은 **조건문 기준 분기와 그 결과**다.

- 시퀀스 다이어그램이 아니라 **`flowchart TD`**.
- **비즈니스 로직만** 그린다. `@Valid` 입력값 검증(400) 분기는 넣지 않는다 — 사용자가 명시적으로 빼 달라고 했다. Service의 판단과 DB 제약에서 갈리는 분기만.
- 마름모 = Service/DB에서 갈리는 조건 (질문형: "같은 코드가 이미 DB에 있음?")
- 결과 칸 = `HTTP 상태 + DB에 일어난 일` ("409 Conflict<br/>코드 중복, 저장 안 됨")
- 흐름이 같은 도메인은 한 장으로 묶는다 (예: Product/Process 등록). 파일 하나에 동작 하나: `NN-<domain>-<action>.md`, 맨 위에 제목과 엔드포인트.
- 코드에서 처리 안 되는 경로(잡히지 않은 예외로 500)를 발견하면 노란 칸으로 표시하고 아래에 한 줄 설명.

템플릿과 색 규칙은 `references/flowchart-template.md`를 읽고 그대로 쓴다. `%%{init ...}%%` 설정 줄은 넣지 않는다.

순서도는 코드를 다 쓴 뒤 **실제 코드를 다시 읽고** 그린다. 다이어그램이 코드와 다르면 검증 도구로서 의미가 없다. 그리다 발견한 코드 문제는 다이어그램에 반영하고 보고한다.

## 5. 마무리

- 커밋하지 않는다 (사용자가 요청할 때만).
- 보고: API 표(Method, 경로, 성공, 실패), 만든 파일 구성 요약, 테스트 결과 수치(동시성 테스트 포함), 순서도 파일 목록, 남은 한계.
