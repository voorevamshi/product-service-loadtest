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
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    // ─── CREATE ─────────────────────────────────────────────────────────────
    @Transactional
    @CacheEvict(value = "product-lists", allEntries = true)
    public ProductResponse createProduct(ProductRequest request) {
        log.debug("[CACHE] Evicting all 'product-lists' entries due to new product");
        if (productRepository.existsBySku(request.getSku())) {
            throw new DuplicateSkuException(request.getSku());
        }
        long dbStart = System.currentTimeMillis();
        Product saved = productRepository.save(mapToEntity(request));
        log.info("[DB] INSERT product id={} sku={}  db_time={}ms",
                saved.getId(), saved.getSku(), System.currentTimeMillis() - dbStart);
        return mapToResponse(saved);
    }

    // ─── READ BY ID ──────────────────────────────────────────────────────────
    @Cacheable(value = "products", key = "#id")
    public ProductResponse getProductById(Long id) {
        // If this log line appears → it's a CACHE MISS (went to DB)
        // If it does NOT appear  → it's a CACHE HIT (served from Caffeine/Redis)
        log.info("[CACHE MISS] products id={}  →  hitting DB", id);
        long dbStart = System.currentTimeMillis();
        ProductResponse result = productRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ProductNotFoundException(id));
        log.debug("[DB] SELECT product id={}  db_time={}ms", id, System.currentTimeMillis() - dbStart);
        return result;
    }

    // ─── READ BY SKU ─────────────────────────────────────────────────────────
    @Cacheable(value = "products", key = "'sku:' + #sku")
    public ProductResponse getProductBySku(String sku) {
        log.info("[CACHE MISS] products sku={}  →  hitting DB", sku);
        long dbStart = System.currentTimeMillis();
        ProductResponse result = productRepository.findBySku(sku)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ProductNotFoundException(sku));
        log.debug("[DB] SELECT product sku={}  db_time={}ms", sku, System.currentTimeMillis() - dbStart);
        return result;
    }

    // ─── LIST ────────────────────────────────────────────────────────────────
    @Cacheable(value = "product-lists",
               key = "'page:'+#page+':size:'+#size+':cat:'+#category" +
                     "+':sort:'+#sortBy+':dir:'+#sortDir" +
                     "+':min:'+#minPrice+':max:'+#maxPrice+':q:'+#search")
    public PageResponse<ProductResponse> getProducts(
            String category, BigDecimal minPrice, BigDecimal maxPrice,
            String search, int page, int size, String sortBy, String sortDir) {

        log.info("[CACHE MISS] product-lists page={} size={} category={}  →  hitting DB",
                page, size, category);

        Sort sortObj = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sortObj);

        long dbStart = System.currentTimeMillis();
        Page<Product> productPage = productRepository.findWithFilters(
                category, minPrice, maxPrice, search, pageable);
        log.debug("[DB] SELECT products list  count={}  db_time={}ms",
                productPage.getTotalElements(), System.currentTimeMillis() - dbStart);

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
    @Caching(evict = {
        @CacheEvict(value = "products",      key = "#id"),
        @CacheEvict(value = "product-lists", allEntries = true)
    })
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        log.debug("[CACHE] Evicting 'products' id={} and all 'product-lists'", id);
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

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

        long dbStart = System.currentTimeMillis();
        Product updated = productRepository.save(existing);
        log.info("[DB] UPDATE product id={}  db_time={}ms",
                updated.getId(), System.currentTimeMillis() - dbStart);
        return mapToResponse(updated);
    }

    // ─── DELETE ─────────────────────────────────────────────────────────────
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "products",      key = "#id"),
        @CacheEvict(value = "product-lists", allEntries = true)
    })
    public void deleteProduct(Long id) {
        log.debug("[CACHE] Evicting 'products' id={} and all 'product-lists'", id);
        if (!productRepository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        productRepository.deleteById(id);
        log.info("[DB] DELETE product id={}", id);
    }

    private Product mapToEntity(ProductRequest req) {
        return Product.builder()
                .name(req.getName()).description(req.getDescription())
                .price(req.getPrice()).sku(req.getSku())
                .category(req.getCategory()).stockQuantity(req.getStockQuantity())
                .build();
    }

    private ProductResponse mapToResponse(Product p) {
        return ProductResponse.builder()
                .id(p.getId()).name(p.getName()).description(p.getDescription())
                .price(p.getPrice()).sku(p.getSku()).category(p.getCategory())
                .stockQuantity(p.getStockQuantity())
                .createdAt(p.getCreatedAt()).updatedAt(p.getUpdatedAt())
                .build();
    }
}
