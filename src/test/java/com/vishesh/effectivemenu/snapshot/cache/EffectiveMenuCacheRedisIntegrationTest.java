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
import com.vishesh.effectivemenu.monitoring.EffectiveMenuOperationalMetrics;

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
                Duration.ofSeconds(60),
                new EffectiveMenuOperationalMetrics());
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
                Duration.ofMillis(100),
                new EffectiveMenuOperationalMetrics());

        shortLivedCache.put(1L, snapshot(1L, 4L));
        Thread.sleep(300);

        assertTrue(shortLivedCache.get(1L).isEmpty());
    }

    @Test
    void whenRedisServerStops_cacheReadReturnsEmptyForPostgresFallback() throws Exception {
        try (GenericContainer<?> outageRedis = new GenericContainer<>("redis:7-alpine")
                .withExposedPorts(6379)) {
            outageRedis.start();

            LettuceConnectionFactory outageConnectionFactory = new LettuceConnectionFactory(
                    outageRedis.getHost(), outageRedis.getMappedPort(6379));
            outageConnectionFactory.setTimeout(2_000L);
            outageConnectionFactory.afterPropertiesSet();

            try {
                StringRedisTemplate outageTemplate = new StringRedisTemplate(outageConnectionFactory);
                outageTemplate.afterPropertiesSet();
                EffectiveMenuCache outageCache = new EffectiveMenuCache(
                        outageTemplate,
                        new EffectiveMenuCacheKey("effective-menu:outlet:"),
                        new ObjectMapper(),
                        true,
                        Duration.ofSeconds(60),
                        new EffectiveMenuOperationalMetrics());

                outageCache.put(1L, snapshot(1L, 4L));
                outageRedis.stop();

                assertTrue(outageCache.get(1L).isEmpty());
            } finally {
                outageConnectionFactory.destroy();
            }
        }
    }

    @Test
    void afterRedisRestart_cacheCanBeWrittenAndReadAgain() throws Exception {
        try (GenericContainer<?> restartedRedis = new GenericContainer<>("redis:7-alpine")
                .withExposedPorts(6379)) {
            restartedRedis.start();

            LettuceConnectionFactory firstConnectionFactory = new LettuceConnectionFactory(
                    restartedRedis.getHost(), restartedRedis.getMappedPort(6379));
            firstConnectionFactory.afterPropertiesSet();

            try {
                EffectiveMenuCache firstCache = cacheFor(firstConnectionFactory);
                firstCache.put(1L, snapshot(1L, 11L));
                restartedRedis.stop();
                assertTrue(firstCache.get(1L).isEmpty());
            } finally {
                firstConnectionFactory.destroy();
            }

            // Restarting a Testcontainers container may assign a different host port,
            // so create a fresh client connection after restart.
            restartedRedis.start();
            LettuceConnectionFactory recoveredConnectionFactory = new LettuceConnectionFactory(
                    restartedRedis.getHost(), restartedRedis.getMappedPort(6379));
            recoveredConnectionFactory.afterPropertiesSet();
            try {
                EffectiveMenuCache recoveredCache = cacheFor(recoveredConnectionFactory);
                recoveredCache.put(1L, snapshot(1L, 12L));
                assertEquals(12L, recoveredCache.get(1L).orElseThrow().getSnapshotVersion());
            } finally {
                recoveredConnectionFactory.destroy();
            }
        }
    }

    private EffectiveMenuCache cacheFor(LettuceConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return new EffectiveMenuCache(
                template,
                new EffectiveMenuCacheKey("effective-menu:outlet:"),
                new ObjectMapper(),
                true,
                Duration.ofSeconds(60),
                new EffectiveMenuOperationalMetrics());
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
