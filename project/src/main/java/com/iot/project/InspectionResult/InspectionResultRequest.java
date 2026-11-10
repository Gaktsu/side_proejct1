package com.iot.project.InspectionResult;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 등록과 수정이 같은 필드를 받는다. 수정은 보내지 않은 선택 필드를 NULL로 교체한다
// 측정값·규격 간 검증은 Service에서 한다
public record InspectionResultRequest(
        @NotBlank @Size(max = 100) String inspectionItem,
        @Size(max = 100) String inspectionMethod,
        @Digits(integer = 12, fraction = 6) BigDecimal numericValue,
        @Size(max = 255) String textValue,
        @Size(max = 30) String unit,
        @Digits(integer = 12, fraction = 6) BigDecimal specMin,
        @Digits(integer = 12, fraction = 6) BigDecimal specMax,
        @Size(max = 255) String specText,
        @NotNull ResultStatus resultStatus,
        LocalDateTime measuredAt
) {
}
