package com.iot.project.QualityCase;

import com.iot.project.IndexingJob.IndexingJob;
import com.iot.project.IndexingJob.IndexingStatus;

import java.time.LocalDateTime;

// embeddingStatus/qdrantStatus: 현재 버전의 검색 반영 상태, job이 없으면(OPEN) null
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
        int sourceVersion,
        IndexingStatus embeddingStatus,
        IndexingStatus qdrantStatus,
        LocalDateTime occurredAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static QualityCaseResponse from(QualityCase qualityCase) {
        return from(qualityCase, null);
    }

    public static QualityCaseResponse from(QualityCase qualityCase, IndexingJob currentJob) {
        return new QualityCaseResponse(qualityCase.getId(), qualityCase.getCaseCode(),
                qualityCase.getProduct().getId(), qualityCase.getManufacturingProcess().getId(),
                qualityCase.getEquipment() == null ? null : qualityCase.getEquipment().getId(),
                qualityCase.getLotNo(), qualityCase.getTitle(), qualityCase.getProblemDescription(),
                qualityCase.getDefectType(), qualityCase.getProductionQuantity(),
                qualityCase.getDefectQuantity(), qualityCase.getRootCause(), qualityCase.getCorrectiveAction(),
                qualityCase.getResultSummary(), qualityCase.getStatus(), qualityCase.getSourceVersion(),
                currentJob == null ? null : currentJob.getEmbeddingStatus(),
                currentJob == null ? null : currentJob.getQdrantStatus(),
                qualityCase.getOccurredAt(), qualityCase.getCreatedAt(), qualityCase.getUpdatedAt());
    }
}
