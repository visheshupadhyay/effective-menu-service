package com.vishesh.effectivemenu.snapshot.cache;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.vishesh.effectivemenu.snapshot.domain.EffectiveMenuSnapshot;
import com.vishesh.effectivemenu.monitoring.EffectiveMenuOperationalMetrics;

import tools.jackson.databind.ObjectMapper;

@Component
public class EffectiveMenuCache {
    private final StringRedisTemplate redisTemplate;
    private final EffectiveMenuCacheKey cacheKey;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final Duration ttl;
    private final EffectiveMenuOperationalMetrics metrics;

    private static final Logger logger = LoggerFactory.getLogger(EffectiveMenuCache.class);

    public EffectiveMenuCache(StringRedisTemplate redisTemplate,
            EffectiveMenuCacheKey cacheKey,
            ObjectMapper objectMapper,
            @Value("${app.effective-menu-cache.enabled:true}") boolean enabled,
            @Value("${app.effective-menu-cache.ttl:60s}") Duration ttl,
            EffectiveMenuOperationalMetrics metrics) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Cache TTL must be positive");
        }
        if (metrics == null) {
            throw new IllegalArgumentException("Metrics must not be null");
        }
        this.redisTemplate = redisTemplate;
        this.cacheKey = cacheKey;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.ttl = ttl;
        this.metrics = metrics;
    }

    public Optional<EffectiveMenuSnapshot> get(Long outletId) {
        String key = cacheKey.createKey(outletId);
        if (!enabled) {
            return Optional.empty();
        }

        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                metrics.recordCacheMiss();
                return Optional.empty();
            }
            try {
                EffectiveMenuSnapshot snapshot = objectMapper.readValue(json, EffectiveMenuSnapshot.class);
                metrics.recordCacheHit();
                return Optional.of(snapshot);
            } catch (Exception deserializationFailure) {
                metrics.recordCacheReadFailure();
                metrics.recordCacheMiss();
                logger.warn("Cached effective menu contains invalid JSON for outlet {}. Evicting it.", outletId,
                        deserializationFailure);
                evict(outletId);
                return Optional.empty();
            }
        } catch (Exception exception) {
            metrics.recordCacheReadFailure();
            metrics.recordCacheMiss();
            logger.warn("Cached effective menu could not be read for outlet {}", outletId, exception);
            return Optional.empty();
        }
    }

    public void put(Long outletId, EffectiveMenuSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Snapshot must not be null");
        }

        try {
            String key = cacheKey.createKey(outletId);
            if (!enabled) {
                return;
            }
            String json = objectMapper.writeValueAsString(snapshot);
            redisTemplate.opsForValue().set(key, json, ttl);
            metrics.recordCacheWrite();
        } catch (Exception exception) {
            metrics.recordCacheWriteFailure();
            logger.warn("Effective menu could not be cached for outlet {}", outletId, exception);
        }
    }

    public void evict(Long outletId) {
        try{
            String key = cacheKey.createKey(outletId);
            if (enabled) {
                redisTemplate.delete(key);
                metrics.recordCacheEviction();
            }
        }
        catch(Exception exception) {
            metrics.recordCacheEvictionFailure();
            logger.warn("Effective menu could not be deleted for outlet {}", outletId, exception);
        }

    }

    /** Attempts to claim the outlet refresh lock with an automatic expiry. */
    public boolean tryAcquireLock(Long outletId, String token, Duration lockTtl) {
        validateLockArguments(outletId, token, lockTtl);
        if (!enabled) {
            return true;
        }
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                    cacheKey.createLockKey(outletId), token, lockTtl);
            return Boolean.TRUE.equals(acquired);
        } catch (Exception exception) {
            logger.warn("Effective menu refresh lock could not be acquired for outlet {}", outletId, exception);
            return false;
        }
    }

    /** Releases the lock only when the supplied token still owns it. */
    public boolean releaseLock(Long outletId, String token) {
        if (outletId == null || outletId <= 0 || token == null || token.isBlank()) {
            throw new IllegalArgumentException("A positive outlet ID and non-blank token are required");
        }
        if (!enabled) {
            return true;
        }
        String scriptText = "if redis.call('get', KEYS[1]) == ARGV[1] then "
                + "return redis.call('del', KEYS[1]) else return 0 end";
        try {
            Long deleted = redisTemplate.execute(
                    new DefaultRedisScript<>(scriptText, Long.class),
                    Collections.singletonList(cacheKey.createLockKey(outletId)),
                    token);
            return Long.valueOf(1L).equals(deleted);
        } catch (Exception exception) {
            logger.warn("Effective menu refresh lock could not be released for outlet {}", outletId, exception);
            return false;
        }
    }

    private void validateLockArguments(Long outletId, String token, Duration lockTtl) {
        if (outletId == null || outletId <= 0) {
            throw new IllegalArgumentException("Outlet ID must be positive");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Lock token must not be blank");
        }
        if (lockTtl == null || lockTtl.isZero() || lockTtl.isNegative()) {
            throw new IllegalArgumentException("Lock TTL must be positive");
        }
    }
}
