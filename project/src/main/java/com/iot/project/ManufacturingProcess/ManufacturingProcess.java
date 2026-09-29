package com.iot.project.ManufacturingProcess;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "manufacturing_process")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ManufacturingProcess {

    // PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    // 공정 코드, UNIQUE
    @Column(length = 50, unique = true, nullable = false)
    private String processCode;

    // 공정명
    @Column(length = 100, nullable = false)
    private String name;

    // 공정 설명
    @Column(columnDefinition = "TEXT")
    private String description;

    // 생산일
    @Column(nullable = false)
    private LocalDateTime createdAt;
}
