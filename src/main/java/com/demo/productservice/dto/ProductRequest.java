package com.demo.productservice.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// ─── Request DTO ─────────────────────────────────────────────────────────────
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100)
    private String name;

    @Size(max = 500)
    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", message = "Price must be non-negative")
    private BigDecimal price;

    @NotBlank(message = "SKU is required")
    @Size(max = 50)
    private String sku;

    @NotBlank(message = "Category is required")
    private String category;

    @Min(0)
    private Integer stockQuantity = 0;
}
