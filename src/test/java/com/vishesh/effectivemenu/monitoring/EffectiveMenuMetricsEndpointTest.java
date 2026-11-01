package com.vishesh.effectivemenu.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class EffectiveMenuMetricsEndpointTest {
    @Test
    void read_returnsCurrentMetricsSnapshot() {
        EffectiveMenuOperationalMetrics metrics = new EffectiveMenuOperationalMetrics(
                Clock.fixed(Instant.parse("2026-01-01T00:00:30Z"), ZoneOffset.UTC));
        metrics.recordDuplicate();

        EffectiveMenuMetricsSnapshot snapshot = new EffectiveMenuMetricsEndpoint(metrics).read();

        assertEquals(1, snapshot.duplicateEventsIgnored());
        assertEquals(0, snapshot.processedEvents());
    }
}
