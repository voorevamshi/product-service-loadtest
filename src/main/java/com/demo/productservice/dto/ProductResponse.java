package com.demo.productservice.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductResponse {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private String sku;
    private String category;
    private Integer stockQuantity;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
