package com.iot.project.Equipment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 소속 공정은 URL의 processId로 받는다
public record EquipmentCreateRequest(
        @NotBlank @Size(max = 50) String equipmentCode,
        @NotBlank @Size(max = 100) String name,
        String description
) {
}
