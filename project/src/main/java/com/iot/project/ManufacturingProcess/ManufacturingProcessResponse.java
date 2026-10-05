package com.iot.project.ManufacturingProcess;

import java.time.LocalDateTime;

public record ManufacturingProcessResponse(
        Long id,
        String processCode,
        String name,
        String description,
        LocalDateTime createdAt
) {
    public static ManufacturingProcessResponse from(ManufacturingProcess process) {
        return new ManufacturingProcessResponse(process.getId(), process.getProcessCode(), process.getName(),
                process.getDescription(), process.getCreatedAt());
    }
}
