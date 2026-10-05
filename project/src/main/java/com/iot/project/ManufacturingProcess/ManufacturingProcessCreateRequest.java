package com.iot.project.ManufacturingProcess;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ManufacturingProcessCreateRequest(
        @NotBlank @Size(max = 50) String processCode,
        @NotBlank @Size(max = 100) String name,
        String description
) {
    public ManufacturingProcess toEntity() {
        return new ManufacturingProcess(processCode, name, description);
    }
}
