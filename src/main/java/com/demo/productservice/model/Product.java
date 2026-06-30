package com.demo.productservice.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products", indexes = {
    @Index(name = "idx_product_sku", columnList = "sku", unique = true),
    @Index(name = "idx_product_category", columnList = "category")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false)
    private String name;

    @Size(max = 500)
    private String description;

    @NotNull
    @DecimalMin("0.0")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @NotBlank
    @Column(unique = true, nullable = false, length = 50)
    private String sku;

    @NotBlank
    @Column(nullable = false, length = 50)
    private String category;

    @Min(0)
    @Column(nullable = false)
    private Integer stockQuantity = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
