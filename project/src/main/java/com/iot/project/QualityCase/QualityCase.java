package com.iot.project.QualityCase;

import com.iot.project.ManufacturingProcess.ManufacturingProcess;
import com.iot.project.Equipment.Equipment;
import com.iot.project.Product.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "quality_case")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QualityCase {

    // PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    // 품질 사례 번호, UNIQUE
    @Column(length = 50, unique = true, nullable = false)
    private String caseCode;

    // Product의 FK
    @JoinColumn(name = "product_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    // Process의 FK
    @JoinColumn(name = "process_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private ManufacturingProcess manufacturingProcess;

    // Equipment의 FK
    @JoinColumn(name = "equipment_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private Equipment equipment;

    // 생산 LOT
    @Column(length = 100, nullable = false)
    private String lotNo;

    // 사례 제목
    @Column(length = 200, nullable = false)
    private String title;

    // 발생한 문제 현상
    @Column(columnDefinition = "TEXT", nullable = false)
    private String problemDescription;

    // 불량 유형
    @Column(length = 50, nullable = false)
    private String defectType;

    // 생산 수량
    @Column(nullable = false)
    private Integer productionQuantity;

    // 불량 수량
    @Column(nullable = false)
    private Integer defectQuantity;

    // 담당자가 확정한 최종 원인
    @Column(columnDefinition = "TEXT")
    private String rootCause;

    // 조치 내용
    @Column(columnDefinition = "TEXT")
    private String correctiveAction;

    // 조치 후 결과
    @Column(columnDefinition = "TEXT")
    private String resultSummary;

    // OPEN / RESOLVED (미해결 / 해결)
    @Column(length = 30, nullable = false)
    @Enumerated(EnumType.STRING)
    private CaseStatus status;

    // 문제 발생 시각
    @Column(nullable = false)
    private LocalDateTime occurredAt;

    // 등록일
    @Column(nullable = false)
    private LocalDateTime createdAt;

    // 수정일
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 신규 품질문제는 OPEN 상태로 생성되고 원인/조치/결과는 비어 있다
    public QualityCase(String caseCode, Product product, ManufacturingProcess manufacturingProcess,
                       Equipment equipment, String lotNo, String title, String problemDescription,
                       String defectType, Integer productionQuantity, Integer defectQuantity,
                       LocalDateTime occurredAt) {
        this.caseCode = caseCode;
        this.product = product;
        this.manufacturingProcess = manufacturingProcess;
        this.equipment = equipment;
        this.lotNo = lotNo;
        this.title = title;
        this.problemDescription = problemDescription;
        this.defectType = defectType;
        this.productionQuantity = productionQuantity;
        this.defectQuantity = defectQuantity;
        this.status = CaseStatus.OPEN;
        this.occurredAt = occurredAt;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }
}
