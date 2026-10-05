package com.iot.project.Product;

import java.time.LocalDateTime;

public record ProductResponse(
        Long id,
        String productCode,
        String name,
        String modelName,
        String description,
        LocalDateTime createdAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getProductCode(), product.getName(),
                product.getModelName(), product.getDescription(), product.getCreatedAt());
    }
}
