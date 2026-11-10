package com.iot.project.InspectionResult;

import com.iot.project.QualityCase.QualityCase;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "inspection_result")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InspectionResult {

    // PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    // QualityCase의 FK
    @JoinColumn(name = "quality_case_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private QualityCase qualityCase;

    // 검사 항목
    @Column(length = 100, nullable = false)
    private String inspectionItem;

    // 검사 방법
    @Column(length = 100)
    private String inspectionMethod;

    // 숫자형 측정값
    @Column(precision = 18, scale = 6)
    private BigDecimal numericValue;

    // 문자형 측정값
    @Column(length = 255)
    private String textValue;

    // 단위
    @Column(length = 30)
    private String unit;

    // 규격 최소값
    @Column(precision = 18, scale = 6)
    private BigDecimal specMin;

    // 규격 최대값
    @Column(precision = 18, scale = 6)
    private BigDecimal specMax;

    // 숫자가 아닌 규격
    @Column(length = 255)
    private String specText;

    // PASS / FAIL
    @Column(length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private ResultStatus resultStatus;

    // 검사 시점
    private LocalDateTime measuredAt;

    // 등록일
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public InspectionResult(QualityCase qualityCase, String inspectionItem, String inspectionMethod,
                            BigDecimal numericValue, String textValue, String unit, BigDecimal specMin,
                            BigDecimal specMax, String specText, ResultStatus resultStatus,
                            LocalDateTime measuredAt) {
        this.qualityCase = qualityCase;
        this.createdAt = LocalDateTime.now();
        update(inspectionItem, inspectionMethod, numericValue, textValue, unit, specMin, specMax, specText,
                resultStatus, measuredAt);
    }

    // 수정 가능한 필드 전체 교체. 소속 사례와 createdAt은 바뀌지 않는다
    public void update(String inspectionItem, String inspectionMethod, BigDecimal numericValue, String textValue,
                       String unit, BigDecimal specMin, BigDecimal specMax, String specText,
                       ResultStatus resultStatus, LocalDateTime measuredAt) {
        this.inspectionItem = inspectionItem;
        this.inspectionMethod = inspectionMethod;
        this.numericValue = numericValue;
        this.textValue = textValue;
        this.unit = unit;
        this.specMin = specMin;
        this.specMax = specMax;
        this.specText = specText;
        this.resultStatus = resultStatus;
        this.measuredAt = measuredAt;
    }
}