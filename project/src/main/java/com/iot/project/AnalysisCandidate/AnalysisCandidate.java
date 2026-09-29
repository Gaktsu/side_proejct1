package com.iot.project.AnalysisCandidate;

import com.iot.project.Analysis.Analysis;
import com.iot.project.QualityCase.QualityCase;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "analysis_candidate", uniqueConstraints = {
        @UniqueConstraint(
                columnNames = {"analysis_id", "candidate_quality_case_id"}
        )
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisCandidate {

    // PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    // Analysis의 FK
    @JoinColumn(name = "analysis_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Analysis analysis;

    // 검색된 과거 QualityCase id
    @JoinColumn(name = "candidate_quality_case_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private QualityCase qualityCase;

    // qdrant 검색 순위
    @Column(nullable = false)
    private Integer qdrantRank;

    // qdrant vector 유사도
    @Column(precision = 8, scale = 6, nullable = false)
    private BigDecimal vectorScore;

    // JEV 평가 점수
    @Column(precision = 8, scale = 6)
    private BigDecimal jevScore;

    // JEV가 근거 부족으로 판단 여부
    @Column(nullable = false)
    private Boolean isNull;

    // 선택 / 제외 이유가 존재할 경우 저장
    @Column(columnDefinition = "TEXT")
    private String decisionReason;

    // 기록 생성 시각
    @Column(nullable = false)
    private LocalDateTime createdAt;
}
