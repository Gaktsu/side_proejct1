# 순서도 템플릿

## 색 규칙

| class | 의미 | 사용처 |
|---|---|---|
| `ok` (초록) | 성공 | 201, 200, 204 |
| `fail` (연한 빨강, **검은 글자**) | 의도된 실패 | 404, 409, 비즈니스 규칙 위반 |
| `bug` (노랑) | 코드가 처리하지 못하는 경로 | 잡히지 않은 예외로 500 |
| `note` (회색) | 참고 메모 | FK 참조 관계 등 |

`bug`가 필요할 때 정의: `classDef bug fill:#fff3cd,stroke:#ffc107,color:#856404`

## 등록 예시 (부모 확인 + 중복 검사)

````markdown
# Equipment 등록

`POST /api/processes/{processId}/equipments`

```mermaid
flowchart TD
    A([등록 요청]) --> C{processId의 공정이<br/>DB에 있음?}
    C -- 아니오 --> R404[/404 Not Found<br/>공정 없음, 저장 안 됨/]
    C -- 예 --> D{같은 설비 코드가<br/>이미 DB에 있음?}
    D -- 예 --> R409[/409 Conflict<br/>코드 중복, 저장 안 됨/]
    D -- 아니오 --> E[해당 공정 소속으로<br/>INSERT 실행]
    E --> F{DB UNIQUE 제약 위반?<br/>동시 요청으로 같은 코드가 먼저 저장된 경우}
    F -- 예 --> R409B[/409 Conflict<br/>코드 중복, 롤백되어 저장 안 됨/]
    F -- 아니오 --> R201[/201 Created<br/>DB에 저장, id·processId 반환/]

    classDef ok fill:#d4edda,stroke:#28a745,color:#155724
    classDef fail fill:#f8d7da,stroke:#dc3545,color:#000000
    class R201 ok
    class R404,R409,R409B fail
```
````

## 삭제 예시 (FK 참조 → 409)

````markdown
# 삭제 (Product / Process / Equipment 공통)

`DELETE /api/products/{id}`, `DELETE /api/processes/{id}`, `DELETE /api/equipments/{id}`

```mermaid
flowchart TD
    A([삭제 요청]) --> B{id에 해당하는 데이터가<br/>DB에 있음?}
    B -- 아니오 --> R404[/404 Not Found<br/>변경 없음/]
    B -- 예 --> C[DELETE 즉시 실행<br/>flush]
    C --> D{다른 테이블이<br/>이 데이터를 참조 중?}
    D -- 예 --> R409[/409 Conflict<br/>사용 중, 롤백되어 삭제 안 됨/]
    D -- 아니오 --> R204[/204 No Content<br/>DB에서 삭제됨/]

    D -.- N[참조 관계<br/>Product ← quality_case<br/>Process ← equipment, quality_case<br/>Equipment ← quality_case]

    classDef ok fill:#d4edda,stroke:#28a745,color:#155724
    classDef fail fill:#f8d7da,stroke:#dc3545,color:#000000
    classDef note fill:#eeeeee,stroke:#999999,color:#333333
    class R204 ok
    class R404,R409 fail
    class N note
```
````

## 표기 규칙

- 시작: `A([... 요청])` (둥근 칸)
- 조건: `{...?}` 질문형, 줄바꿈은 `<br/>`
- 처리 단계: `[...]` — 결과를 가르는 DB 동작(INSERT, DELETE+flush)만. 내부 변환 같은 사소한 단계는 생략
- 결과: `[/상태코드 이름<br/>DB 변화/]` (평행사변형)
- 화살표 라벨: `-- 예 -->`, `-- 아니오 -->`
