package com.iot.project.QualityCase;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.iot.project.InspectionResult.InspectionResultResponse;

import java.util.List;

// 단건 조회 전용: 기존 사례 필드를 그대로 펼치고 검사 결과 전부(최신 등록순)를 붙인다
public record QualityCaseDetailResponse(
        @JsonUnwrapped QualityCaseResponse qualityCase,
        List<InspectionResultResponse> inspectionResults
) {
}
