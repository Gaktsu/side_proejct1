# AI 기반 품질사례 지식화 및 원인조사 지원 시스템

자동차 부품 제조기업의 품질 담당자가 과거 품질사례를 활용해 새로운 품질 문제의 원인을 조사할 수 있도록 지원하는 시스템입니다.

AI가 최종 원인을 판단하는 것이 아니라, **유사한 과거 사례를 검색하고 원인 후보와 추가 확인사항을 제시하여 담당자의 의사결정을 지원하는 것**을 목표로 합니다.

## 주요 흐름

```text
품질 문제 등록
→ Embedding 생성
→ Qdrant 유사 사례 검색
→ JEV 후보 검증
→ RAG + LLM 분석
→ 원인 후보 및 확인사항 제시
→ 담당자 최종 판단
→ 해결 사례 재축적
```

## 주요 기능

- 품질사례 등록 / 조회 / 수정 / 삭제
- 제품, 공정, 설비 및 검사결과 관리
- Embedding 기반 유사 사례 검색
- Qdrant 기반 Vector Search
- JEV를 활용한 검색 후보 검증
- RAG + LLM 기반 원인 후보 및 확인사항 생성
- 담당자 최종 판단 결과 저장
- 해결 사례 재축적

## 기술 스택

### Backend
- Java 21
- Spring Boot
- Spring Data JPA
- MySQL
- Validation
- Lombok

### AI / Search
- Embedding
- Qdrant
- JEV
- RAG
- LLM

### Frontend
- React

### Deployment
- Docker
- AWS EC2

## MVP 목표

약 3개월 동안 다음 핵심 흐름이 정상적으로 동작하는 MVP를 구현합니다.

> 사례 축적 → 의미 기반 검색 → 후보 검증 → AI 원인조사 지원 → 담당자 판단 → 해결 사례 재축적

실제 기업의 MES, QMS, ERP와 직접 연동하지 않고, 가상 품질 데이터를 활용하여 핵심 기능을 검증합니다.

## 개발 상태

현재 요구사항, DB 구조, API 및 UI 초안 설계를 완료하고 기본 백엔드 구현을 진행 중입니다.
