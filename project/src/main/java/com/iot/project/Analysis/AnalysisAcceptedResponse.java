package com.iot.project.Analysis;

// 202 접수 응답. 검색 결과는 GET /api/analyses/{analysisId}로 조회한다
public record AnalysisAcceptedResponse(Long analysisId, AnalysisStatus status) {

    public static AnalysisAcceptedResponse from(Analysis analysis) {
        return new AnalysisAcceptedResponse(analysis.getId(), analysis.getAnalysisStatus());
    }
}
