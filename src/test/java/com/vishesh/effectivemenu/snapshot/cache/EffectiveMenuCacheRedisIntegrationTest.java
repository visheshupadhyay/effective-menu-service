package com.vishesh.effectivemenu.snapshot.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.vishesh.effectivemenu.snapshot.domain.EffectiveMenuSnapshot;

import tools.jackson.databind.ObjectMapper;

@Testcontainers
class EffectiveMenuCacheRedisIntegrationTest {
    @SuppressWarnings("resource")
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    private EffectiveMenuCache cache;
    private StringRedisTemplate redisTemplate;
    private LettuceConnectionFactory connectionFactory;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(
                redis.getHost(), redis.getMappedPort(6379));

        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();

        cache = new EffectiveMenuCache(
                redisTemplate,
                new EffectiveMenuCacheKey("effective-menu:outlet:"),
                new ObjectMapper(),
                true,
                Duration.ofSeconds(60));
    }

    @AfterEach
    void closeRedisConnectionFactory() {
        connectionFactory.destroy();
    }

    @Test
    void putThenGet_returnsSameSnapshot() throws Exception {
        EffectiveMenuSnapshot snapshot = snapshot(1L, 4L);

        cache.put(1L, snapshot);

        EffectiveMenuSnapshot loaded = cache.get(1L).orElseThrow();
        assertEquals(1L, loaded.getOutletId());
        assertEquals(4L, loaded.getSnapshotVersion());
    }

    @Test
    void evict_removesSnapshot() throws Exception {
        cache.put(1L, snapshot(1L, 4L));

        cache.evict(1L);

        assertTrue(cache.get(1L).isEmpty());
    }

    @Test
    void differentOutlets_useDifferentKeys() throws Exception {
        cache.put(1L, snapshot(1L, 4L));
        cache.put(2L, snapshot(2L, 9L));

        assertEquals(4L, cache.get(1L).orElseThrow().getSnapshotVersion());
        assertEquals(9L, cache.get(2L).orElseThrow().getSnapshotVersion());
    }

    @Test
    void valueExpiresAfterConfiguredTtl() throws Exception {
        EffectiveMenuCache shortLivedCache = new EffectiveMenuCache(
                redisTemplate,
                new EffectiveMenuCacheKey("effective-menu:outlet:"),
                new ObjectMapper(),
                true,
                Duration.ofMillis(100));

        shortLivedCache.put(1L, snapshot(1L, 4L));
        Thread.sleep(300);

        assertTrue(shortLivedCache.get(1L).isEmpty());
    }

    private EffectiveMenuSnapshot snapshot(Long outletId, Long version) throws Exception {
        return new EffectiveMenuSnapshot(
                outletId,
                version,
                new ObjectMapper().readTree("{\"items\":[]}"),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"));
    }
}
