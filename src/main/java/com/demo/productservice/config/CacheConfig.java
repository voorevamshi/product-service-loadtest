package com.demo.productservice.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    // ─────────────────────────────────────────────────────────────────────────
    // PRODUCTION PROFILE  →  Redis (ElastiCache)
    //
    // With autoscaling you have N instances sharing ONE Redis cluster.
    // @CacheEvict on any instance → Redis entry deleted → ALL other instances
    // see the fresh value on next read. No stale data problem.
    //
    // Architecture:
    //   Instance A ──┐
    //   Instance B ──┼──► Redis (ElastiCache) ◄──► RDS
    //   Instance C ──┘
    //
    // Each instance talks to the SAME Redis keys, so:
    //   - Update on Instance A → evicts Redis key
    //   - Read on Instance B   → Redis miss → DB hit → repopulates Redis
    //   - Read on Instance C   → Redis HIT ✅ (same fresh value for everyone)
    // ─────────────────────────────────────────────────────────────────────────
    @Bean
    @Profile("prod")
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {

        // Jackson serializer that stores type info so deserialization works
        // for any cached type (ProductResponse, PageResponse, etc.)
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(mapper);

        // Default config applied to ALL caches unless overridden below
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(60))           // 60s TTL
                .disableCachingNullValues()                  // never cache null
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(jsonSerializer));

        // Per-cache TTL overrides — tune based on how often data changes
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

        // Products change infrequently → 60s TTL is fine
        cacheConfigs.put("products", defaultConfig.entryTtl(Duration.ofSeconds(60)));

        // Product listings (search/filter) change more often → shorter TTL
        cacheConfigs.put("product-lists", defaultConfig.entryTtl(Duration.ofSeconds(30)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .transactionAware()   // cache writes happen only if DB tx commits
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LOCAL / TEST PROFILE  →  Caffeine (in-memory, no Redis needed locally)
    //
    // Run locally:  java -jar app.jar  (default profile = "local")
    // Run on EC2:   java -jar app.jar --spring.profiles.active=prod
    // ─────────────────────────────────────────────────────────────────────────
    @Bean
    @Profile({"local", "default", "test"})
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("products", "product-lists");
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .recordStats());
        return manager;
    }
}
