package com.iot.project.QualityCase;

// 유사 사례 한 건: Qdrant 코사인 유사도 + MySQL의 사례 내용
public record SimilarCaseResponse(double score, QualityCaseResponse qualityCase) {
}
