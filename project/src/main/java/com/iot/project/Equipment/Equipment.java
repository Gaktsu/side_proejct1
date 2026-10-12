package com.iot.project.Equipment;

import com.iot.project.ManufacturingProcess.ManufacturingProcess;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "equipment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Equipment {

    // PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    // Process의 FK
    @JoinColumn(name = "process_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private ManufacturingProcess manufacturingProcess;

    // 설비 코드, UNIQUE
    @Column(length = 50, unique = true, nullable = false)
    private String equipmentCode;

    // 설비명
    @Column(length = 100, nullable = false)
    private String name;

    // 설비 설명
    @Column(columnDefinition = "TEXT")
    private String description;

    // 등록일
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Equipment(ManufacturingProcess manufacturingProcess, String equipmentCode, String name, String description) {
        this.manufacturingProcess = manufacturingProcess;
        this.equipmentCode = equipmentCode;
        this.name = name;
        this.description = description;
        this.createdAt = LocalDateTime.now();
    }

    // 설비 코드와 소속 공정은 수정하지 않는다 (공정을 바꾸면 이 설비를 쓰는 품질사례의 공정과 어긋난다)
    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
