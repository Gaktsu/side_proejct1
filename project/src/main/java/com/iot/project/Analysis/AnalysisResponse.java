package com.iot.project.Analysis;

import com.iot.project.AnalysisCandidate.AnalysisCandidate;
import com.iot.project.AnalysisCandidate.InvalidReason;
import com.iot.project.QualityCase.QualityCase;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AnalysisResponse(
        Long id,
        Long qualityCaseId,
        AnalysisStatus status,
        String errorMessage,
        LocalDateTime createdAt,
        List<Candidate> candidates
) {

    // vectorScore: Qdrant cosine 유사도 (분석·디버깅용). jevScore·isNull은 JEV 도입 전이라 null
    public record Candidate(
            Integer qdrantRank,
            Long qualityCaseId,
            String caseCode,
            String title,
            BigDecimal vectorScore,
            InvalidReason invalidReason,
            BigDecimal jevScore,
            Boolean isNull
    ) {
        static Candidate from(AnalysisCandidate candidate) {
            QualityCase qualityCase = candidate.getQualityCase();
            return new Candidate(candidate.getQdrantRank(), qualityCase.getId(), qualityCase.getCaseCode(),
                    qualityCase.getTitle(), candidate.getVectorScore(), candidate.getInvalidReason(),
                    candidate.getJevScore(), candidate.getIsNull());
        }
    }

    public static AnalysisResponse from(Analysis analysis, List<AnalysisCandidate> candidates) {
        return new AnalysisResponse(analysis.getId(), analysis.getQualityCase().getId(), analysis.getAnalysisStatus(),
                analysis.getErrorMessage(), analysis.getCreatedAt(), candidates.stream().map(Candidate::from).toList());
    }
}
