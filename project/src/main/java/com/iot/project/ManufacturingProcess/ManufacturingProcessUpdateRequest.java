package com.iot.project.ManufacturingProcess;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 수정 가능한 필드를 모두 받아 통째로 교체한다 (공정 코드는 수정 불가)
public record ManufacturingProcessUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        String description
) {
}
