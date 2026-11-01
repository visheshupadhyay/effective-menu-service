package com.vishesh.effectivemenu.monitoring;

import java.time.Instant;

/** Immutable values returned by the Effective Menu monitoring endpoint. */
public record EffectiveMenuMetricsSnapshot(
        long processedEvents,
        long duplicateEventsIgnored,
        long staleEventsIgnored,
        long consumerFailures,
        long dlqEvents,
        Instant lastProcessedAt,
        Instant lastFailureAt,
        long lastPropagationLagSeconds) {
}
