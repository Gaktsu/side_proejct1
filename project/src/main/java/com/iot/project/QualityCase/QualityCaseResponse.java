package com.iot.project.QualityCase;

import java.time.LocalDateTime;

public record QualityCaseResponse(
        Long id,
        String caseCode,
        Long productId,
        Long processId,
        Long equipmentId,
        String lotNo,
        String title,
        String problemDescription,
        String defectType,
        Integer productionQuantity,
        Integer defectQuantity,
        CaseStatus status,
        LocalDateTime occurredAt,
        LocalDateTime createdAt
) {
    public static QualityCaseResponse from(QualityCase qualityCase) {
        return new QualityCaseResponse(qualityCase.getId(), qualityCase.getCaseCode(),
                qualityCase.getProduct().getId(), qualityCase.getManufacturingProcess().getId(),
                qualityCase.getEquipment() == null ? null : qualityCase.getEquipment().getId(),
                qualityCase.getLotNo(), qualityCase.getTitle(), qualityCase.getProblemDescription(),
                qualityCase.getDefectType(), qualityCase.getProductionQuantity(),
                qualityCase.getDefectQuantity(), qualityCase.getStatus(), qualityCase.getOccurredAt(),
                qualityCase.getCreatedAt());
    }
}
