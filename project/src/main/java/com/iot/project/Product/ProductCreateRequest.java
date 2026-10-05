package com.iot.project.Product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductCreateRequest(
        @NotBlank @Size(max = 50) String productCode,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 100) String modelName,
        String description
) {
    public Product toEntity() {
        return new Product(productCode, name, modelName, description);
    }
}
