package com.vishesh.effectivemenu.snapshot.application;

import java.time.Clock;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.vishesh.effectivemenu.snapshot.cache.EffectiveMenuCache;
import com.vishesh.effectivemenu.snapshot.domain.EffectiveMenuSnapshot;
import com.vishesh.effectivemenu.catalog.event.EventScheduleWindow;
import com.vishesh.effectivemenu.snapshot.schedule.WeeklyScheduleEvaluator;
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
    private final WeeklyScheduleEvaluator scheduleEvaluator;
    private final Clock clock;

    @Autowired
    public EffectiveMenuSnapshotService(
            @Qualifier("effectiveMenuSnapshotPersistenceAdapter") EffectiveMenuSnapshotRepository repository,
            EffectiveMenuCache cache,
            @Value("${app.effective-menu-cache.lock-enabled:true}") boolean lockEnabled,
            @Value("${app.effective-menu-cache.lock-ttl:5s}") Duration lockTtl,
            @Value("${app.effective-menu-cache.lock-wait-timeout:2s}") Duration lockWaitTimeout,
            @Value("${app.effective-menu-cache.lock-retry-interval:50ms}") Duration lockRetryInterval,
            WeeklyScheduleEvaluator scheduleEvaluator) {
        this(repository, cache, lockEnabled, lockTtl, lockWaitTimeout, lockRetryInterval,
                scheduleEvaluator, Clock.systemUTC());
    }

    EffectiveMenuSnapshotService(EffectiveMenuSnapshotRepository repository, EffectiveMenuCache cache,
            boolean lockEnabled, Duration lockTtl, Duration lockWaitTimeout, Duration lockRetryInterval,
            WeeklyScheduleEvaluator scheduleEvaluator, Clock clock) {
        if (lockTtl == null || lockTtl.isZero() || lockTtl.isNegative()
                || lockWaitTimeout == null || lockWaitTimeout.isNegative()
                || lockRetryInterval == null || lockRetryInterval.isNegative()) {
            throw new IllegalArgumentException("Cache lock durations must be valid");
        }
        if (scheduleEvaluator == null || clock == null) {
            throw new IllegalArgumentException("Schedule evaluator and clock are required");
        }
        this.repository = repository;
        this.cache = cache;
        this.lockEnabled = lockEnabled;
        this.lockTtl = lockTtl;
        this.lockWaitTimeout = lockWaitTimeout;
        this.lockRetryInterval = lockRetryInterval;
        this.scheduleEvaluator = scheduleEvaluator;
        this.clock = clock;
    }

    /**
     * Lightweight constructor used by unit tests that do not exercise Redis
     * locking.
     */
    EffectiveMenuSnapshotService(EffectiveMenuSnapshotRepository repository, EffectiveMenuCache cache) {
        this(repository, cache, false, Duration.ofSeconds(5), Duration.ZERO, Duration.ZERO,
                new WeeklyScheduleEvaluator(), Clock.systemUTC());
    }

    public EffectiveMenuSnapshotService(EffectiveMenuSnapshotRepository repository, EffectiveMenuCache cache,
            boolean lockEnabled, Duration lockTtl, Duration lockWaitTimeout, Duration lockRetryInterval) {
        this(repository, cache, lockEnabled, lockTtl, lockWaitTimeout, lockRetryInterval,
                new WeeklyScheduleEvaluator(), Clock.systemUTC());
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

    /**
     * Returns a customer-safe copy without hidden, unavailable, or archived items.
     */
    public EffectiveMenuSnapshot getCustomerSnapshot(Long outletId) {
        EffectiveMenuSnapshot snapshot = getSnapshot(outletId);
        JsonNode menuCopy = snapshot.getEffectiveMenu().deepCopy();
        if (!menuCopy.isObject()
                || !menuCopy.has("items")
                || !menuCopy.get("items").isArray()) {
            return snapshot;
        }

        ArrayNode items = (ArrayNode) menuCopy.get("items");
        for (int index = items.size() - 1; index >= 0; index--) {
            JsonNode node = items.get(index);
            if (!(node instanceof ObjectNode item)) {
                items.remove(index);
                continue;
            }
            applyCurrentSchedule(item);
            if (!isCustomerVisible(item)) {
                items.remove(index);
            }
        }

        return new EffectiveMenuSnapshot(snapshot.getOutletId(), snapshot.getSnapshotVersion(),
                menuCopy, snapshot.getCalculatedAt(), snapshot.getUpdatedAt());
    }

    private boolean isCustomerVisible(ObjectNode item) {
        // Customers can still see unavailable items as sold out; visibility controls
        // hiding.
        return item.path("visibility").asBoolean(true)
                && !"ARCHIVED".equals(item.path("status").asString());
    }

    /** Evaluates persisted schedule metadata at read time, even when Redis holds the snapshot. */
    private void applyCurrentSchedule(ObjectNode item) {
        JsonNode scheduleNode = item.remove("_schedule");
        if (!(scheduleNode instanceof ObjectNode schedule)) {
            return;
        }
        try {
            String timezone = schedule.path("timezone").asString(null);
            boolean catalogAvailable = schedule.path("catalogAvailability").asBoolean(true);
            Map<String, List<EventScheduleWindow>> windows = readWindows(schedule.get("windows"));
            boolean scheduleActive = windows.values().stream().anyMatch(day -> !day.isEmpty())
                    ? scheduleEvaluator.isActive(windows, timezone, clock)
                    : true;
            item.put("availability", catalogAvailable && scheduleActive);
        } catch (RuntimeException ignored) {
            // Preserve the last durable availability if corrupted internal metadata is encountered.
        }
    }

    private Map<String, List<EventScheduleWindow>> readWindows(JsonNode windowsNode) {
        Map<String, List<EventScheduleWindow>> windows = new LinkedHashMap<>();
        if (windowsNode == null || !windowsNode.isObject()) {
            return windows;
        }
        windowsNode.properties().forEach(entry -> {
            String weekday = entry.getKey();
            JsonNode node = entry.getValue();
            List<EventScheduleWindow> dayWindows = new ArrayList<>();
            if (node.isArray()) {
                node.forEach(window -> dayWindows.add(new EventScheduleWindow(
                        LocalTime.parse(window.path("startTime").asString()),
                        LocalTime.parse(window.path("endTime").asString()))));
            }
            windows.put(weekday, dayWindows);
        });
        return windows;
    }
}
