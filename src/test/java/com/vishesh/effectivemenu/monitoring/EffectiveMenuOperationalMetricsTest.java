package com.vishesh.effectivemenu.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class EffectiveMenuOperationalMetricsTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:30Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void newMetrics_startWithZeroCounters() {
        EffectiveMenuMetricsSnapshot snapshot = new EffectiveMenuOperationalMetrics(CLOCK).snapshot();

        assertEquals(0, snapshot.processedEvents());
        assertEquals(0, snapshot.duplicateEventsIgnored());
        assertEquals(0, snapshot.staleEventsIgnored());
        assertEquals(0, snapshot.consumerFailures());
        assertEquals(0, snapshot.dlqEvents());
        assertEquals(0, snapshot.cacheHits());
        assertEquals(0, snapshot.cacheMisses());
        assertEquals(0, snapshot.cacheReadFailures());
        assertEquals(0, snapshot.cacheWrites());
        assertEquals(0, snapshot.cacheWriteFailures());
        assertEquals(0, snapshot.cacheEvictions());
        assertEquals(0, snapshot.cacheEvictionFailures());
        assertEquals(0, snapshot.lastPropagationLagSeconds());
    }

    @Test
    void recordMethods_updateCountersAndTimestamps() {
        EffectiveMenuOperationalMetrics metrics = new EffectiveMenuOperationalMetrics(CLOCK);

        metrics.recordProcessed(Instant.parse("2026-01-01T00:00:20Z"));
        metrics.recordDuplicate();
        metrics.recordStale();
        metrics.recordFailure();
        metrics.recordDlq();
        metrics.recordCacheHit();
        metrics.recordCacheMiss();
        metrics.recordCacheReadFailure();
        metrics.recordCacheWrite();
        metrics.recordCacheWriteFailure();
        metrics.recordCacheEviction();
        metrics.recordCacheEvictionFailure();

        EffectiveMenuMetricsSnapshot snapshot = metrics.snapshot();
        assertEquals(1, snapshot.processedEvents());
        assertEquals(1, snapshot.duplicateEventsIgnored());
        assertEquals(1, snapshot.staleEventsIgnored());
        assertEquals(1, snapshot.consumerFailures());
        assertEquals(1, snapshot.dlqEvents());
        assertEquals(1, snapshot.cacheHits());
        assertEquals(1, snapshot.cacheMisses());
        assertEquals(1, snapshot.cacheReadFailures());
        assertEquals(1, snapshot.cacheWrites());
        assertEquals(1, snapshot.cacheWriteFailures());
        assertEquals(1, snapshot.cacheEvictions());
        assertEquals(1, snapshot.cacheEvictionFailures());
        assertEquals(NOW, snapshot.lastProcessedAt());
        assertEquals(NOW, snapshot.lastFailureAt());
        assertEquals(10, snapshot.lastPropagationLagSeconds());
    }

    @Test
    void nullClock_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> new EffectiveMenuOperationalMetrics(null));
    }
}
