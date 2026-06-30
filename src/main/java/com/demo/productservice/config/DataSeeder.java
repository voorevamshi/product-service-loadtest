package com.demo.productservice.config;

import com.demo.productservice.model.Product;
import com.demo.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final ProductRepository repository;

    @Override
    public void run(String... args) {
        if (repository.count() > 0) return;

        List<Product> products = List.of(
            product("Laptop Pro 15", "High-performance laptop", "1299.99", "LP-001", "Electronics", 50),
            product("Wireless Mouse", "Ergonomic wireless mouse", "29.99", "WM-002", "Electronics", 200),
            product("Mechanical Keyboard", "RGB mechanical keyboard", "89.99", "MK-003", "Electronics", 150),
            product("USB-C Hub 7-in-1", "Multi-port USB-C hub", "49.99", "UH-004", "Electronics", 100),
            product("Running Shoes", "Lightweight running shoes", "119.99", "RS-005", "Footwear", 75),
            product("Office Chair", "Ergonomic mesh chair", "349.99", "OC-006", "Furniture", 30),
            product("Standing Desk", "Height-adjustable desk", "599.99", "SD-007", "Furniture", 20),
            product("Coffee Maker", "12-cup programmable", "79.99", "CM-008", "Kitchen", 60),
            product("Noise Cancelling Headphones", "Premium ANC headphones", "249.99", "NC-009", "Electronics", 80),
            product("Water Bottle 32oz", "Insulated stainless steel", "39.99", "WB-010", "Sports", 300)
        );

        repository.saveAll(products);
        log.info("Seeded {} products into database", products.size());
    }

    private Product product(String name, String desc, String price, String sku,
                            String category, int stock) {
        return Product.builder()
                .name(name).description(desc)
                .price(new BigDecimal(price))
                .sku(sku).category(category)
                .stockQuantity(stock)
                .build();
    }
}
