package com.demo.productservice.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine in-process cache.
 *
 * Why this matters on t3.micro:
 *  - Without cache: every GET hits H2/DB → at 200 QPS the HikariCP pool
 *    (max 10 connections) saturates → callers queue → latency spikes → timeouts.
 *  - With cache: hot products served from heap in <1 ms, zero DB round-trip.
 *
 * Trade-off: cache lives in the same 700 MB heap → too large a maximumSize
 * causes GC pressure. Keep it bounded (500 entries here).
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("products");
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .recordStats());   // Stats visible at /actuator/metrics/cache.*
        return manager;
    }
}
