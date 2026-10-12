package com.iot.project.Equipment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 수정 가능한 필드를 모두 받아 통째로 교체한다 (설비 코드, 소속 공정은 수정 불가)
public record EquipmentUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        String description
) {
}
