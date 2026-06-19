package com.buddyai.agent.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Application-level cache configuration backed by Caffeine.
 *
 * <p>A single shared {@link CaffeineCacheManager} is registered with sensible
 * defaults: up to 1 000 entries per cache, entries expire 10 minutes after
 * they were last written, and hit/miss statistics are collected for Actuator
 * exposure via {@code management.metrics.cache.instrument-caffeine}.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Primary {@link CacheManager} used by all {@code @Cacheable} / {@code @CacheEvict}
     * annotations in the application.
     *
     * @return a Caffeine-backed cache manager
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats());
        return manager;
    }
}
