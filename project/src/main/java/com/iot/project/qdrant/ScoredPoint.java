package com.iot.project.qdrant;

// 검색 결과 한 건: 유사한 사례의 id(= QualityCase.id)와 코사인 유사도
public record ScoredPoint(long id, double score) {
}
