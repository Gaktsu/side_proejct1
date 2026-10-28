package com.iot.project.QualityCase;

import java.time.LocalDateTime;

public record QualityCaseRevisionResponse(
        Integer version,
        Long productId,
        Long processId,
        Long equipmentId,
        String lotNo,
        String title,
        String problemDescription,
        String defectType,
        Integer productionQuantity,
        Integer defectQuantity,
        LocalDateTime occurredAt,
        String rootCause,
        String correctiveAction,
        String resultSummary,
        LocalDateTime createdAt
) {
    public static QualityCaseRevisionResponse from(QualityCaseRevision revision) {
        return new QualityCaseRevisionResponse(revision.getVersion(), revision.getProduct().getId(),
                revision.getManufacturingProcess().getId(),
                revision.getEquipment() == null ? null : revision.getEquipment().getId(),
                revision.getLotNo(), revision.getTitle(), revision.getProblemDescription(), revision.getDefectType(),
                revision.getProductionQuantity(), revision.getDefectQuantity(), revision.getOccurredAt(),
                revision.getRootCause(), revision.getCorrectiveAction(), revision.getResultSummary(),
                revision.getCreatedAt());
    }
}
