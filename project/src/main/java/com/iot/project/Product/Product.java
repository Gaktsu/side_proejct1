package com.iot.project.Product;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "product")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    // PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    // 제품 코드, UNIQUE
    @Column(length = 50, unique = true, nullable = false)
    private String productCode;

    // 제품명
    @Column(length = 100, nullable = false)
    private String name;

    // 모델명
    @Column(length = 100)
    private String modelName;

    // 제품 설명
    @Column(columnDefinition = "TEXT")
    private String description;

    // 생산일
    @Column(nullable = false)
    private LocalDateTime createdAt;
}
