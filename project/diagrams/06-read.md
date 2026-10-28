# 조회 (Product / Process / Equipment / QualityCase 공통)

## 단건 조회

`GET /api/products/{id}`, `GET /api/processes/{id}`, `GET /api/equipments/{id}`, `GET /api/quality-cases/{id}`

```mermaid
flowchart TD
    A([단건 조회 요청]) --> B{id에 해당하는 데이터가<br/>DB에 있음?}
    B -- 아니오 --> R404[/404 Not Found<br/>변경 없음/]
    B -- 예 --> R200[/200 OK<br/>해당 데이터 반환/]

    classDef ok fill:#d4edda,stroke:#28a745,color:#155724
    classDef fail fill:#f8d7da,stroke:#dc3545,color:#000000
    class R200 ok
    class R404 fail
```

## 목록 조회

`GET /api/products`, `GET /api/processes`, `GET /api/quality-cases`, `GET /api/processes/{processId}/equipments`

```mermaid
flowchart TD
    A([목록 조회 요청]) --> B{설비 목록 요청임?}
    B -- 아니오 --> R200[/200 OK<br/>전체 목록 반환, 없으면 빈 배열/]
    B -- 예 --> C{processId의 공정이<br/>DB에 있음?}
    C -- 아니오 --> R404[/404 Not Found<br/>공정 없음/]
    C -- 예 --> R200E[/200 OK<br/>해당 공정 소속 설비 목록, 없으면 빈 배열/]

    R200 -.- N[정렬<br/>제품·공정·설비: id 오름차순<br/>품질사례: id 내림차순 최신순<br/>페이징·필터 없음]

    classDef ok fill:#d4edda,stroke:#28a745,color:#155724
    classDef fail fill:#f8d7da,stroke:#dc3545,color:#000000
    classDef note fill:#eeeeee,stroke:#999999,color:#333333
    class R200,R200E ok
    class R404 fail
    class N note
```

## 품질사례 이력 조회

`GET /api/quality-cases/{id}/revisions`

```mermaid
flowchart TD
    A([이력 조회 요청]) --> B{id의 품질사례가<br/>DB에 있음?}
    B -- 아니오 --> R404[/404 Not Found/]
    B -- 예 --> R200[/200 OK<br/>RESOLVED 수정 직전 값들, 버전 내림차순<br/>없으면 빈 배열/]

    classDef ok fill:#d4edda,stroke:#28a745,color:#155724
    classDef fail fill:#f8d7da,stroke:#dc3545,color:#000000
    class R200 ok
    class R404 fail
```

- 품질사례 단건·목록 응답에는 현재 버전의 `embeddingStatus`/`qdrantStatus`가 함께 온다 (job 없으면 null). 현재 버전 job은 사례당 최대 1개라 left join 한 번으로 조회한다.

