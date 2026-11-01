package com.vishesh.effectivemenu.snapshot.cache;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.vishesh.effectivemenu.snapshot.domain.EffectiveMenuSnapshot;

import tools.jackson.databind.ObjectMapper;

@Component
public class EffectiveMenuCache {
    private final StringRedisTemplate redisTemplate;
    private final EffectiveMenuCacheKey cacheKey;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final Duration ttl;


    public EffectiveMenuCache(StringRedisTemplate redisTemplate,
        EffectiveMenuCacheKey cacheKey,
        ObjectMapper objectMapper,
        @Value("${app.effective-menu-cache.enabled:true}") boolean enabled,
        @Value("${app.effective-menu-cache.ttl:60s}") Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Cache TTL must be positive");
        }
        this.redisTemplate = redisTemplate;
        this.cacheKey = cacheKey;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.ttl = ttl;
    }

    public Optional<EffectiveMenuSnapshot> get(Long outletId) {
        String key = cacheKey.createKey(outletId);
        if (!enabled) {
            return Optional.empty();
        }
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, EffectiveMenuSnapshot.class));
        } catch (Exception exception) {
            throw new IllegalStateException("Cached effective menu could not be read", exception);
        }
    }

    public void put(Long outletId, EffectiveMenuSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Snapshot must not be null");
        }
        String key = cacheKey.createKey(outletId);
        if (!enabled) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(snapshot);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (Exception exception) {
            throw new IllegalStateException("Effective menu could not be cached", exception);
        }
    }

    public void evict(Long outletId) {
        String key = cacheKey.createKey(outletId);
        if (enabled) {
            redisTemplate.delete(key);
        }
    }
}
