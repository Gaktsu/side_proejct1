package com.iot.project.QualityCase;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

// 수정 가능한 필드를 모두 받아 통째로 교체한다 (equipmentId를 null로 보내면 설비 연결 해제)
public record QualityCaseUpdateRequest(
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
