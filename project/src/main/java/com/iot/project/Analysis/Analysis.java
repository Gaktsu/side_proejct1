package com.iot.project.Analysis;

import com.iot.project.QualityCase.QualityCase;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Entity
@Table(name = "analysis")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Analysis {
    // PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    // QualityCase의 FK
    @JoinColumn(name = "target_quality_case_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private QualityCase qualityCase;

    // 분석 당시 사용한 입력 내용
    @Column(columnDefinition = "TEXT", nullable = false)
    private String queryText;

    // 분석 상태
    @Column(length = 30, nullable = false)
    @Enumerated(EnumType.STRING)
    private AnalysisStatus analysisStatus;

    // AI가 생성한 원인 후보
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<String> aiRootCauseCandidates;

    // 추가 확인사항
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<String> aiCheckItems;

    // AI 분석 요약
    @Column(columnDefinition = "TEXT")
    private String aiSummary;

    // 분석 실행 시각
    @Column(nullable = false)
    private LocalDateTime createdAt;
}
