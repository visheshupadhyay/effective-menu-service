package com.vishesh.effectivemenu.snapshot.application;

import java.util.Optional;
import java.util.UUID;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.vishesh.effectivemenu.snapshot.cache.EffectiveMenuCache;
import com.vishesh.effectivemenu.snapshot.domain.EffectiveMenuSnapshot;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Reads stored outlet menus and prepares the customer-safe response. */
@Service
public class EffectiveMenuSnapshotService {
    private final EffectiveMenuSnapshotRepository repository;
    private final EffectiveMenuCache cache;
    private final boolean lockEnabled;
    private final Duration lockTtl;
    private final Duration lockWaitTimeout;
    private final Duration lockRetryInterval;

    @Autowired
    public EffectiveMenuSnapshotService(
        @Qualifier ("effectiveMenuSnapshotPersistenceAdapter") EffectiveMenuSnapshotRepository repository,
        EffectiveMenuCache cache,
        @Value("${app.effective-menu-cache.lock-enabled:true}") boolean lockEnabled,
        @Value("${app.effective-menu-cache.lock-ttl:5s}") Duration lockTtl,
        @Value("${app.effective-menu-cache.lock-wait-timeout:2s}") Duration lockWaitTimeout,
        @Value("${app.effective-menu-cache.lock-retry-interval:50ms}") Duration lockRetryInterval) {
        if (lockTtl == null || lockTtl.isZero() || lockTtl.isNegative()
                || lockWaitTimeout == null || lockWaitTimeout.isNegative()
                || lockRetryInterval == null || lockRetryInterval.isNegative()) {
            throw new IllegalArgumentException("Cache lock durations must be valid");
        }
        this.repository = repository;
        this.cache = cache;
        this.lockEnabled = lockEnabled;
        this.lockTtl = lockTtl;
        this.lockWaitTimeout = lockWaitTimeout;
        this.lockRetryInterval = lockRetryInterval;
    }

    /** Lightweight constructor used by unit tests that do not exercise Redis locking. */
    EffectiveMenuSnapshotService(EffectiveMenuSnapshotRepository repository, EffectiveMenuCache cache) {
        this(repository, cache, false, Duration.ofSeconds(5), Duration.ZERO, Duration.ZERO);
    }

    public EffectiveMenuSnapshot getSnapshot(Long outletId) {
        if (outletId == null || outletId <= 0) {
            throw new IllegalArgumentException("Outlet ID must be positive");
        }

        Optional<EffectiveMenuSnapshot> optional = cache.get(outletId);

        if (optional.isPresent()) {
            return optional.get();
        }

        if (!lockEnabled) {
            return loadAndCache(outletId);
        }

        String token = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + lockWaitTimeout.toNanos();
        boolean ownsLock = false;
        try {
            while (true) {
                ownsLock = cache.tryAcquireLock(outletId, token, lockTtl);
                if (ownsLock) {
                    Optional<EffectiveMenuSnapshot> refreshed = cache.get(outletId);
                    return refreshed.orElseGet(() -> loadAndCache(outletId));
                }

                Optional<EffectiveMenuSnapshot> refreshed = cache.get(outletId);
                if (refreshed.isPresent()) {
                    return refreshed.get();
                }
                if (System.nanoTime() >= deadline) {
                    return loadAndCache(outletId);
                }
                sleepBeforeRetry();
            }
        } finally {
            if (ownsLock) {
                cache.releaseLock(outletId, token);
            }
        }

    }

    private EffectiveMenuSnapshot loadAndCache(Long outletId) {
        EffectiveMenuSnapshot snapshot = repository.findByOutletId(outletId)
                .orElseThrow(() -> new EffectiveMenuSnapshotUnavailableException(outletId));
        cache.put(outletId, snapshot);
        return snapshot;
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(lockRetryInterval.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for menu cache refresh", exception);
        }

    }

    /** Returns a customer-safe copy without hidden, unavailable, or archived items. */
    public EffectiveMenuSnapshot getCustomerSnapshot(Long outletId) {
        EffectiveMenuSnapshot snapshot = getSnapshot(outletId);
        JsonNode menuCopy = snapshot.getEffectiveMenu().deepCopy();
        if (!menuCopy.isObject() || !menuCopy.has("items") || !menuCopy.get("items").isArray()) {
            return snapshot;
        }

        ArrayNode items = (ArrayNode) menuCopy.get("items");
        for (int index = items.size() - 1; index >= 0; index--) {
            JsonNode node = items.get(index);
            if (!(node instanceof ObjectNode item) || !isCustomerVisible(item)) {
                items.remove(index);
            }
        }

        return new EffectiveMenuSnapshot(snapshot.getOutletId(), snapshot.getSnapshotVersion(),
                menuCopy, snapshot.getCalculatedAt(), snapshot.getUpdatedAt());
    }

    private boolean isCustomerVisible(ObjectNode item) {
        // Customers can still see unavailable items as sold out; visibility controls hiding.
        return item.path("visibility").asBoolean(true)
                && !"ARCHIVED".equals(item.path("status").asString());
    }
}
