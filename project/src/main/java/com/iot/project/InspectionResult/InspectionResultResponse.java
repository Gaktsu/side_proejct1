package com.iot.project.InspectionResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InspectionResultResponse(
        Long id,
        Long qualityCaseId,
        String inspectionItem,
        String inspectionMethod,
        BigDecimal numericValue,
        String textValue,
        String unit,
        BigDecimal specMin,
        BigDecimal specMax,
        String specText,
        ResultStatus resultStatus,
        LocalDateTime measuredAt,
        LocalDateTime createdAt
) {
    public static InspectionResultResponse from(InspectionResult result) {
        return new InspectionResultResponse(result.getId(), result.getQualityCase().getId(),
                result.getInspectionItem(), result.getInspectionMethod(), result.getNumericValue(),
                result.getTextValue(), result.getUnit(), result.getSpecMin(), result.getSpecMax(),
                result.getSpecText(), result.getResultStatus(), result.getMeasuredAt(), result.getCreatedAt());
    }
}
