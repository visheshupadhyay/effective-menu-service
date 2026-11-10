package com.vishesh.effectivemenu.monitoring;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

/** Thread-safe counters used to understand event processing at runtime. */
@Component
public class EffectiveMenuOperationalMetrics {
    private final Clock clock;
    private final AtomicLong processedEvents = new AtomicLong();
    private final AtomicLong duplicateEventsIgnored = new AtomicLong();
    private final AtomicLong staleEventsIgnored = new AtomicLong();
    private final AtomicLong consumerFailures = new AtomicLong();
    private final AtomicLong dlqEvents = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    private final AtomicLong cacheReadFailures = new AtomicLong();
    private final AtomicLong cacheWrites = new AtomicLong();
    private final AtomicLong cacheWriteFailures = new AtomicLong();
    private final AtomicLong cacheEvictions = new AtomicLong();
    private final AtomicLong cacheEvictionFailures = new AtomicLong();
    private final AtomicReference<Instant> lastProcessedAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastFailureAt = new AtomicReference<>();
    private final AtomicLong lastPropagationLagSeconds = new AtomicLong();

    public EffectiveMenuOperationalMetrics() {
        this(Clock.systemUTC());
    }

    public EffectiveMenuOperationalMetrics(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("Clock cannot be null");
        }
        this.clock = clock;
    }

    public void recordProcessed(Instant eventOccurredAt) {
        Instant now = Instant.now(clock);
        processedEvents.incrementAndGet();
        lastProcessedAt.set(now);

        if (eventOccurredAt == null) {
            lastPropagationLagSeconds.set(0);
            return;
        }

        long lag = Duration.between(eventOccurredAt, now).getSeconds();
        lastPropagationLagSeconds.set(Math.max(0, lag));
    }

    public void recordDuplicate() {
        duplicateEventsIgnored.incrementAndGet();
    }

    public void recordStale() {
        staleEventsIgnored.incrementAndGet();
    }

    public void recordFailure() {
        consumerFailures.incrementAndGet();
        lastFailureAt.set(Instant.now(clock));
    }

    public void recordDlq() {
        dlqEvents.incrementAndGet();
    }

    public void recordCacheHit() {
        cacheHits.incrementAndGet();
    }

    public void recordCacheMiss() {
        cacheMisses.incrementAndGet();
    }

    public void recordCacheReadFailure() {
        cacheReadFailures.incrementAndGet();
    }

    public void recordCacheWrite() {
        cacheWrites.incrementAndGet();
    }

    public void recordCacheWriteFailure() {
        cacheWriteFailures.incrementAndGet();
    }

    public void recordCacheEviction() {
        cacheEvictions.incrementAndGet();
    }

    public void recordCacheEvictionFailure() {
        cacheEvictionFailures.incrementAndGet();
    }

    public EffectiveMenuMetricsSnapshot snapshot() {
        return new EffectiveMenuMetricsSnapshot(
                processedEvents.get(),
                duplicateEventsIgnored.get(),
                staleEventsIgnored.get(),
                consumerFailures.get(),
                dlqEvents.get(),
                cacheHits.get(),
                cacheMisses.get(),
                cacheReadFailures.get(),
                cacheWrites.get(),
                cacheWriteFailures.get(),
                cacheEvictions.get(),
                cacheEvictionFailures.get(),
                lastProcessedAt.get(),
                lastFailureAt.get(),
                lastPropagationLagSeconds.get());
    }
}
