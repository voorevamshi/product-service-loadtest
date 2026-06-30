package com.demo.productservice.controller;

import com.demo.productservice.dto.*;
import com.demo.productservice.service.ProductService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Product REST API — all endpoints guarded by Resilience4j rate limiter.
 *
 * The @RateLimiter("productApi") annotation enforces the 150 req/s cap
 * defined in application.yml. Requests beyond the cap receive HTTP 429
 * (handled in GlobalExceptionHandler) instead of silently piling up and
 * exhausting the JVM heap or Tomcat threads.
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // ── POST /api/v1/products ────────────────────────────────────────────────
    @PostMapping
    @RateLimiter(name = "productApi")
    public ResponseEntity<ApiResponse<ProductResponse>> create(
            @Valid @RequestBody ProductRequest request) {
        ProductResponse response = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Product created successfully"));
    }

    // ── GET /api/v1/products/{id} ────────────────────────────────────────────
    @GetMapping("/{id}")
    @RateLimiter(name = "productApi")
    public ResponseEntity<ApiResponse<ProductResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.success(productService.getProductById(id), "Product fetched"));
    }

    // ── GET /api/v1/products/sku/{sku} ───────────────────────────────────────
    @GetMapping("/sku/{sku}")
    @RateLimiter(name = "productApi")
    public ResponseEntity<ApiResponse<ProductResponse>> getBySku(@PathVariable String sku) {
        return ResponseEntity.ok(
                ApiResponse.success(productService.getProductBySku(sku), "Product fetched"));
    }

    // ── GET /api/v1/products ─────────────────────────────────────────────────
    // Supports: ?category=&minPrice=&maxPrice=&search=&page=0&size=20&sortBy=name&sortDir=asc
    @GetMapping
    @RateLimiter(name = "productApi")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        // Guard: cap page size to prevent accidentally huge DB reads under load
        size = Math.min(size, 100);

        PageResponse<ProductResponse> result = productService.getProducts(
                category, minPrice, maxPrice, search, page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success(result, "Products listed"));
    }

    // ── PUT /api/v1/products/{id} ────────────────────────────────────────────
    @PutMapping("/{id}")
    @RateLimiter(name = "productApi")
    public ResponseEntity<ApiResponse<ProductResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(productService.updateProduct(id, request), "Product updated"));
    }

    // ── DELETE /api/v1/products/{id} ─────────────────────────────────────────
    @DeleteMapping("/{id}")
    @RateLimiter(name = "productApi")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Product deleted"));
    }
}
