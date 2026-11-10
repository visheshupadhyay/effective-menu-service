package com.vishesh.effectivemenu.snapshot.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.vishesh.effectivemenu.monitoring.EffectiveMenuOperationalMetrics;
import com.vishesh.effectivemenu.snapshot.application.EffectiveMenuSnapshotRepository;
import com.vishesh.effectivemenu.snapshot.application.EffectiveMenuSnapshotService;
import com.vishesh.effectivemenu.snapshot.domain.EffectiveMenuSnapshot;

import tools.jackson.databind.ObjectMapper;

/**
 * Prints local, approximate latency comparisons for the main cache paths.
 * This is intentionally not a production-capacity test; timings depend on the
 * developer machine and Docker Desktop.
 */
@Testcontainers
class EffectiveMenuCacheBenchmarkTest {
    private static final long OUTLET_ID = 501L;
    private static final int WARMUP_CALLS = 10;
    private static final int MEASURED_CALLS = 100;

    @SuppressWarnings("resource")
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private EffectiveMenuCache cache;
    private EffectiveMenuSnapshot snapshot;

    @BeforeEach
    void setUp() throws Exception {
        connectionFactory = new LettuceConnectionFactory(
                redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();

        cache = new EffectiveMenuCache(
                redisTemplate,
                new EffectiveMenuCacheKey("benchmark:effective-menu:outlet:"),
                new ObjectMapper(),
                true,
                Duration.ofMinutes(1),
                new EffectiveMenuOperationalMetrics());
        snapshot = new EffectiveMenuSnapshot(
                OUTLET_ID,
                10L,
                new ObjectMapper().readTree("{\"items\":[{\"id\":1,\"price\":12.00}] }"),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    @AfterEach
    void closeConnectionFactory() {
        connectionFactory.destroy();
    }

    @Test
    void compareCacheHitMissAndFallbackPaths() {
        cache.put(OUTLET_ID, snapshot);

        warmUp(() -> assertTrue(cache.get(OUTLET_ID).isPresent()));
        long cacheHitNanos = averageNanos(() ->
                assertEquals(snapshot.getSnapshotVersion(),
                        cache.get(OUTLET_ID).orElseThrow().getSnapshotVersion()));

        cache.evict(OUTLET_ID);
        warmUp(() -> assertTrue(cache.get(OUTLET_ID).isEmpty()));
        long cacheMissNanos = averageNanos(() -> assertTrue(cache.get(OUTLET_ID).isEmpty()));

        EffectiveMenuSnapshotRepository repository = mock(EffectiveMenuSnapshotRepository.class);
        when(repository.findByOutletId(OUTLET_ID)).thenReturn(Optional.of(snapshot));
        EffectiveMenuSnapshotService service = new EffectiveMenuSnapshotService(
                repository, cache, false, Duration.ofSeconds(5), Duration.ZERO, Duration.ZERO);
        long fallbackNanos = averageNanos(() -> {
            cache.evict(OUTLET_ID);
            assertSame(snapshot, service.getSnapshot(OUTLET_ID));
        });

        System.out.printf("%nEffective-menu local benchmark%n"
                + "  Redis cache hit: %.3f ms%n"
                + "  Redis cache miss: %.3f ms%n"
                + "  PostgreSQL fallback path: %.3f ms%n",
                millis(cacheHitNanos), millis(cacheMissNanos), millis(fallbackNanos));
    }

    @Test
    void compareRedisUnavailableFallbackPath() {
        try (GenericContainer<?> outageRedis = new GenericContainer<>("redis:7-alpine")
                .withExposedPorts(6379)) {
            outageRedis.start();
            LettuceConnectionFactory outageFactory = new LettuceConnectionFactory(
                    outageRedis.getHost(), outageRedis.getMappedPort(6379));
            // Keep the outage sample short; a production timeout is configured separately.
            outageFactory.setTimeout(100L);
            outageFactory.afterPropertiesSet();
            try {
                StringRedisTemplate outageTemplate = new StringRedisTemplate(outageFactory);
                outageTemplate.afterPropertiesSet();
                EffectiveMenuCache outageCache = new EffectiveMenuCache(
                        outageTemplate,
                        new EffectiveMenuCacheKey("benchmark:outage:outlet:"),
                        new ObjectMapper(),
                        true,
                        Duration.ofMinutes(1),
                        new EffectiveMenuOperationalMetrics());
                EffectiveMenuSnapshotRepository repository = mock(EffectiveMenuSnapshotRepository.class);
                when(repository.findByOutletId(OUTLET_ID)).thenReturn(Optional.of(snapshot));
                EffectiveMenuSnapshotService service = new EffectiveMenuSnapshotService(
                        repository, outageCache, false, Duration.ofSeconds(5), Duration.ZERO, Duration.ZERO);

                outageRedis.stop();
                long start = System.nanoTime();
                assertSame(snapshot, service.getSnapshot(OUTLET_ID));
                long elapsed = System.nanoTime() - start;

                System.out.printf("Redis unavailable fallback: %.3f ms%n", millis(elapsed));
            } finally {
                outageFactory.destroy();
            }
        }
    }

    private void warmUp(Runnable operation) {
        for (int index = 0; index < WARMUP_CALLS; index++) {
            operation.run();
        }
    }

    private long averageNanos(Runnable operation) {
        long total = 0L;
        for (int index = 0; index < MEASURED_CALLS; index++) {
            long start = System.nanoTime();
            operation.run();
            total += System.nanoTime() - start;
        }
        return total / MEASURED_CALLS;
    }

    private double millis(long nanos) {
        return nanos / 1_000_000.0;
    }
}
