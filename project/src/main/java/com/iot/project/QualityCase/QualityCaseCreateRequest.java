package com.iot.project.QualityCase;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

// 사례번호(caseCode)와 상태(status)는 서버가 정한다
public record QualityCaseCreateRequest(
        @NotNull Long productId,
        @NotNull Long processId,
        Long equipmentId,
        @NotBlank @Size(max = 100) String lotNo,
        @NotBlank @Size(max = 200) String title,
        @NotBlank String problemDescription,
        @NotBlank @Size(max = 50) String defectType,
        @NotNull @PositiveOrZero Integer productionQuantity,
        @NotNull @PositiveOrZero Integer defectQuantity,
        @NotNull LocalDateTime occurredAt
) {
}
