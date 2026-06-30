package com.demo.productservice.service;

import com.demo.productservice.dto.*;
import com.demo.productservice.exception.DuplicateSkuException;
import com.demo.productservice.exception.ProductNotFoundException;
import com.demo.productservice.model.Product;
import com.demo.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)  // Default read-only; mutations override below
public class ProductService {

    private final ProductRepository productRepository;

    // ─── CREATE ─────────────────────────────────────────────────────────────
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new DuplicateSkuException(request.getSku());
        }
        Product product = mapToEntity(request);
        Product saved = productRepository.save(product);
        log.info("Created product id={} sku={}", saved.getId(), saved.getSku());
        return mapToResponse(saved);
    }

    // ─── READ BY ID (cached) ─────────────────────────────────────────────────
    // Cache dramatically reduces DB load at high QPS for hot products
    @Cacheable(value = "products", key = "#id")
    public ProductResponse getProductById(Long id) {
        return productRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    // ─── READ BY SKU ─────────────────────────────────────────────────────────
    @Cacheable(value = "products", key = "'sku:' + #sku")
    public ProductResponse getProductBySku(String sku) {
        return productRepository.findBySku(sku)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ProductNotFoundException(sku));
    }

    // ─── LIST WITH FILTERS + PAGINATION ─────────────────────────────────────
    public PageResponse<ProductResponse> getProducts(
            String category, BigDecimal minPrice, BigDecimal maxPrice,
            String search, int page, int size, String sortBy, String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Product> productPage = productRepository.findWithFilters(
                category, minPrice, maxPrice, search, pageable);

        List<ProductResponse> content = productPage.getContent()
                .stream().map(this::mapToResponse).toList();

        return PageResponse.<ProductResponse>builder()
                .content(content)
                .page(productPage.getNumber())
                .size(productPage.getSize())
                .totalElements(productPage.getTotalElements())
                .totalPages(productPage.getTotalPages())
                .last(productPage.isLast())
                .build();
    }

    // ─── UPDATE ─────────────────────────────────────────────────────────────
    @Transactional
    @CacheEvict(value = "products", key = "#id")
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        // SKU uniqueness check (skip if SKU unchanged)
        if (!existing.getSku().equals(request.getSku())
                && productRepository.existsBySku(request.getSku())) {
            throw new DuplicateSkuException(request.getSku());
        }

        existing.setName(request.getName());
        existing.setDescription(request.getDescription());
        existing.setPrice(request.getPrice());
        existing.setSku(request.getSku());
        existing.setCategory(request.getCategory());
        existing.setStockQuantity(request.getStockQuantity());

        Product updated = productRepository.save(existing);
        log.info("Updated product id={}", updated.getId());
        return mapToResponse(updated);
    }

    // ─── DELETE ─────────────────────────────────────────────────────────────
    @Transactional
    @CacheEvict(value = "products", key = "#id")
    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        productRepository.deleteById(id);
        log.info("Deleted product id={}", id);
    }

    // ─── MAPPER HELPERS ──────────────────────────────────────────────────────
    private Product mapToEntity(ProductRequest req) {
        return Product.builder()
                .name(req.getName())
                .description(req.getDescription())
                .price(req.getPrice())
                .sku(req.getSku())
                .category(req.getCategory())
                .stockQuantity(req.getStockQuantity())
                .build();
    }

    private ProductResponse mapToResponse(Product p) {
        return ProductResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .price(p.getPrice())
                .sku(p.getSku())
                .category(p.getCategory())
                .stockQuantity(p.getStockQuantity())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
