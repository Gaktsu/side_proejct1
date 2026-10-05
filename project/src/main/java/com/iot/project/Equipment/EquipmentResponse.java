package com.iot.project.Equipment;

import java.time.LocalDateTime;

public record EquipmentResponse(
        Long id,
        Long processId,
        String equipmentCode,
        String name,
        String description,
        LocalDateTime createdAt
) {
    public static EquipmentResponse from(Equipment equipment) {
        return new EquipmentResponse(equipment.getId(), equipment.getManufacturingProcess().getId(),
                equipment.getEquipmentCode(), equipment.getName(), equipment.getDescription(),
                equipment.getCreatedAt());
    }
}
