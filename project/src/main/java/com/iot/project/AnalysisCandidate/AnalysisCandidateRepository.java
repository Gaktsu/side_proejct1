package com.iot.project.AnalysisCandidate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AnalysisCandidateRepository extends JpaRepository<AnalysisCandidate, Long> {

    // Qdrant 순서대로, 후보 사례까지 한 번에 (N+1 없음)
    @Query("""
            select c from AnalysisCandidate c join fetch c.qualityCase
            where c.analysis.id = :analysisId order by c.qdrantRank""")
    List<AnalysisCandidate> findWithCaseByAnalysisId(Long analysisId);

    // 재요청 시 분석보다 먼저 지운다 (FK)
    @Modifying(flushAutomatically = true)
    @Query("delete from AnalysisCandidate c where c.analysis.id = :analysisId")
    void deleteByAnalysisIdNow(Long analysisId);
}
