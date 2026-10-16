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
        String rootCause,
        String correctiveAction,
        String resultSummary,
        CaseStatus status,
        LocalDateTime occurredAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static QualityCaseResponse from(QualityCase qualityCase) {
        return new QualityCaseResponse(qualityCase.getId(), qualityCase.getCaseCode(),
                qualityCase.getProduct().getId(), qualityCase.getManufacturingProcess().getId(),
                qualityCase.getEquipment() == null ? null : qualityCase.getEquipment().getId(),
                qualityCase.getLotNo(), qualityCase.getTitle(), qualityCase.getProblemDescription(),
                qualityCase.getDefectType(), qualityCase.getProductionQuantity(),
                qualityCase.getDefectQuantity(), qualityCase.getRootCause(), qualityCase.getCorrectiveAction(),
                qualityCase.getResultSummary(), qualityCase.getStatus(), qualityCase.getOccurredAt(),
                qualityCase.getCreatedAt(), qualityCase.getUpdatedAt());
    }
}
