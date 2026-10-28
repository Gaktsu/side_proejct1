package com.iot.project.QualityCase;

import com.iot.project.Equipment.Equipment;
import com.iot.project.ManufacturingProcess.ManufacturingProcess;
import com.iot.project.Product.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// RESOLVED 사례가 수정되기 직전 버전의 내용. 추가만 하고 고치거나 지우지 않는다 (조회만, 검색 대상 아님)
@Getter
@Entity
@Table(name = "quality_case_revision", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"quality_case_id", "version"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QualityCaseRevision {

    // PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    // QualityCase의 FK
    @JoinColumn(name = "quality_case_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private QualityCase qualityCase;

    // 대체되기 전 sourceVersion
    @Column(nullable = false)
    private Integer version;

    @JoinColumn(name = "product_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    @JoinColumn(name = "process_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private ManufacturingProcess manufacturingProcess;

    @JoinColumn(name = "equipment_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private Equipment equipment;

    @Column(length = 100, nullable = false)
    private String lotNo;

    @Column(length = 200, nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String problemDescription;

    @Column(length = 50, nullable = false)
    private String defectType;

    @Column(nullable = false)
    private Integer productionQuantity;

    @Column(nullable = false)
    private Integer defectQuantity;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Column(columnDefinition = "TEXT")
    private String rootCause;

    @Column(columnDefinition = "TEXT")
    private String correctiveAction;

    @Column(columnDefinition = "TEXT")
    private String resultSummary;

    // 이 버전이 대체된 시각
    @Column(nullable = false)
    private LocalDateTime createdAt;

    // 수정 직전의 사례 값을 그대로 복사한다
    public QualityCaseRevision(QualityCase qualityCase) {
        this.qualityCase = qualityCase;
        this.version = qualityCase.getSourceVersion();
        this.product = qualityCase.getProduct();
        this.manufacturingProcess = qualityCase.getManufacturingProcess();
        this.equipment = qualityCase.getEquipment();
        this.lotNo = qualityCase.getLotNo();
        this.title = qualityCase.getTitle();
        this.problemDescription = qualityCase.getProblemDescription();
        this.defectType = qualityCase.getDefectType();
        this.productionQuantity = qualityCase.getProductionQuantity();
        this.defectQuantity = qualityCase.getDefectQuantity();
        this.occurredAt = qualityCase.getOccurredAt();
        this.rootCause = qualityCase.getRootCause();
        this.correctiveAction = qualityCase.getCorrectiveAction();
        this.resultSummary = qualityCase.getResultSummary();
        this.createdAt = LocalDateTime.now();
    }
}
