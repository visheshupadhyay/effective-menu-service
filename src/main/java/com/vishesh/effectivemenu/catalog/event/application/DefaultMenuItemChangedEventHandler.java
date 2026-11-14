package com.vishesh.effectivemenu.catalog.event.application;

import java.time.Instant;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.vishesh.effectivemenu.catalog.event.EventScheduleWindow;
import com.vishesh.effectivemenu.catalog.event.MenuItemChangedEvent;
import com.vishesh.effectivemenu.catalog.event.MenuItemStatus;
import com.vishesh.effectivemenu.snapshot.application.EffectiveMenuSnapshotRepository;
import com.vishesh.effectivemenu.snapshot.cache.EffectiveMenuCache;
import com.vishesh.effectivemenu.snapshot.domain.EffectiveMenuSnapshot;
import com.vishesh.effectivemenu.monitoring.EffectiveMenuOperationalMetrics;
import com.vishesh.effectivemenu.snapshot.schedule.EffectiveScheduleResolver;
import com.vishesh.effectivemenu.snapshot.schedule.WeeklyScheduleEvaluator;
import com.vishesh.effectivemenu.snapshot.schedule.SeasonalAvailabilityEvaluator;

import jakarta.transaction.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Applies one validated Catalog event to exactly one outlet snapshot. */
@Service("defaultMenuItemChangedEventHandler")
public class DefaultMenuItemChangedEventHandler implements MenuItemChangedEventHandler {
    private final EffectiveMenuSnapshotRepository effectiveMenuSnapshotRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final EffectiveMenuOperationalMetrics metrics;
    private final EffectiveMenuCache cache;
    private final Clock clock;
    private final WeeklyScheduleEvaluator scheduleEvaluator;
    private final EffectiveScheduleResolver scheduleResolver;
    private final SeasonalAvailabilityEvaluator seasonalEvaluator;

    @Autowired
    public DefaultMenuItemChangedEventHandler(
            @Qualifier("effectiveMenuSnapshotPersistenceAdapter") EffectiveMenuSnapshotRepository effectiveMenuSnapshotRepository,
            @Qualifier("processedEventPersistenceAdapter") ProcessedEventRepository processedEventRepository,
            EffectiveMenuOperationalMetrics metrics,
            EffectiveMenuCache cache,
            WeeklyScheduleEvaluator scheduleEvaluator,
            EffectiveScheduleResolver scheduleResolver) {
        this(effectiveMenuSnapshotRepository, processedEventRepository, metrics, cache, Clock.systemUTC(),
                scheduleEvaluator, scheduleResolver);
    }

    DefaultMenuItemChangedEventHandler(
            EffectiveMenuSnapshotRepository effectiveMenuSnapshotRepository,
            ProcessedEventRepository processedEventRepository,
            EffectiveMenuOperationalMetrics metrics,
            EffectiveMenuCache cache,
            Clock clock) {
        this(effectiveMenuSnapshotRepository, processedEventRepository, metrics, cache, clock,
                new WeeklyScheduleEvaluator(), new EffectiveScheduleResolver());
    }

    DefaultMenuItemChangedEventHandler(
            EffectiveMenuSnapshotRepository effectiveMenuSnapshotRepository,
            ProcessedEventRepository processedEventRepository,
            EffectiveMenuOperationalMetrics metrics,
            EffectiveMenuCache cache,
            Clock clock,
            WeeklyScheduleEvaluator scheduleEvaluator,
            EffectiveScheduleResolver scheduleResolver) {

        this.effectiveMenuSnapshotRepository = effectiveMenuSnapshotRepository;
        this.processedEventRepository = processedEventRepository;
        this.metrics = metrics;
        this.cache = cache;
        this.clock = clock;
        this.scheduleEvaluator = scheduleEvaluator;
        this.scheduleResolver = scheduleResolver;
        this.seasonalEvaluator = new SeasonalAvailabilityEvaluator();
    }

    @Override
    @Transactional
    public void handle(MenuItemChangedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        if (event.getOutletId() == null || event.getOutletId() <= 0) {
            throw new IllegalArgumentException("Event outlet ID must be positive");
        }

        if (processedEventRepository.exists(event.getEventId())) {
            metrics.recordDuplicate();
            return;
        }

        Optional<EffectiveMenuSnapshot> optional = effectiveMenuSnapshotRepository.findByOutletId(event.getOutletId());

        if (optional.isEmpty()) {
            throw new EffectiveMenuSnapshotNotFoundException(event.getOutletId());
        }

        EffectiveMenuSnapshot snapshot = optional.get();

        if (event.getAggregateVersion() <= snapshot.getSnapshotVersion()) {
            metrics.recordStale();
            markProcessed(event);
            return;
        }

        JsonNode copiedMenu = snapshot.getEffectiveMenu().deepCopy();
        if (!copiedMenu.isObject() || !copiedMenu.has("items") || !copiedMenu.get("items").isArray()) {
            throw new IllegalArgumentException("Effective menu must contain an items array");
        }

        JsonNode itemsNode = copiedMenu.get("items");

        ArrayNode items = (ArrayNode) itemsNode;

        Long itemId = event.getItemId();

        ObjectNode item = findItem(items, itemId);

        if (item == null) {
            throw new IllegalArgumentException("Menu item is not present in the outlet snapshot");
        }

        if (event.getStatus() == MenuItemStatus.ARCHIVED) {
            removeItem(items, itemId);
        } else {
            if (event.getItemName() != null) {
                item.put("name", event.getItemName());

            }

            if (event.getPrice() != null) {
                item.put("price", event.getPrice());
            }

            if (Boolean.TRUE.equals(event.getScheduleChanged())) {
                Map<String, List<EventScheduleWindow>> effectiveWindows = scheduleResolver.resolve(
                        event.getBaseScheduleWindows(), event.getOutletScheduleWindows());
                if (effectiveWindows.values().stream().allMatch(List::isEmpty)
                        && !event.getScheduleWindows().isEmpty()) {
                    effectiveWindows = event.getScheduleWindows();
                }
                storeSchedule(item, effectiveWindows, event.getScheduleTimezone(), event.getAvailability());
                // An empty schedule means that no schedule restriction exists. Keep
                // the current catalog availability instead of turning the item off.
                if (hasAnyWindows(effectiveWindows)) {
                    boolean scheduleActive = scheduleEvaluator.isActive(
                            effectiveWindows, event.getScheduleTimezone(), clock.instant());
                    boolean catalogAvailable = event.getAvailability() == null
                            || Boolean.TRUE.equals(event.getAvailability());
                    item.put("availability", scheduleActive && catalogAvailable);
                }
            }

            if (Boolean.TRUE.equals(event.getSeasonalAvailabilityChanged())) {
                storeSeasonalAvailability(item, event.getSeasonalStartDate(), event.getSeasonalEndDate(),
                        event.getSeasonalTimezone());
                boolean catalogAvailable = readCatalogAvailability(item, event.getAvailability());
                boolean seasonActive = seasonalEvaluator.isActive(event.getSeasonalStartDate(),
                        event.getSeasonalEndDate(), event.getSeasonalTimezone(), clock.instant());
                item.put("availability", catalogAvailable && seasonActive);
            } else if (!Boolean.TRUE.equals(event.getScheduleChanged()) && event.getAvailability() != null) {
                item.put("availability", event.getAvailability());
                updateStoredCatalogAvailability(item, event.getAvailability());
            }

            if (event.getVisibility() != null) {
                item.put("visibility", event.getVisibility());
            }

            item.put("status", event.getStatus().name());
            if (event.getStatus() == MenuItemStatus.HIDDEN
                    || event.getStatus() == MenuItemStatus.DRAFT) {
                item.put("visibility", false);
            }
        }

        Instant now = clock.instant();
        effectiveMenuSnapshotRepository.save(new EffectiveMenuSnapshot(
                snapshot.getOutletId(),
                event.getAggregateVersion(),
                copiedMenu,
                snapshot.getCalculatedAt(),
                now));
        cache.evict(event.getOutletId());

        markProcessed(event);
        metrics.recordProcessed(event.getOccurredAt());
    }

    private ObjectNode findItem(ArrayNode items, Long itemId) {
        for (JsonNode item : items) {
            if (item.isObject() && item.path("id").asLong() == itemId) {
                return (ObjectNode) item;
            }
        }
        return null;
    }

    private void removeItem(ArrayNode items, Long itemId) {
        for (int index = items.size() - 1; index >= 0; index--) {
            JsonNode item = items.get(index);
            if (item.isObject() && item.path("id").asLong() == itemId) {
                items.remove(index);
                return;
            }
        }
    }

    /**
     * Stores enough schedule data in the read model to re-evaluate it as time
     * passes.
     */
    private void storeSchedule(ObjectNode item, Map<String, List<EventScheduleWindow>> windows,
            String timezone, Boolean availability) {
        ObjectNode schedule = item.putObject("_schedule");
        schedule.put("timezone", timezone);
        schedule.put("catalogAvailability", availability == null || Boolean.TRUE.equals(availability));
        ObjectNode windowsNode = schedule.putObject("windows");
        windows.forEach((weekday, dayWindows) -> {
            ArrayNode dayNode = windowsNode.putArray(weekday);
            dayWindows.forEach(window -> {
                ObjectNode windowNode = dayNode.addObject();
                windowNode.put("startTime", window.startTime().toString());
                windowNode.put("endTime", window.endTime().toString());
            });
        });
    }

    private void updateStoredCatalogAvailability(ObjectNode item, Boolean availability) {
        JsonNode scheduleNode = item.get("_schedule");
        if (scheduleNode instanceof ObjectNode schedule) {
            schedule.put("catalogAvailability", Boolean.TRUE.equals(availability));
        }
    }

    private void storeSeasonalAvailability(ObjectNode item, java.time.LocalDate startDate,
            java.time.LocalDate endDate, String timezone) {
        ObjectNode seasonal = item.putObject("_seasonalAvailability");
        seasonal.put("timezone", timezone);
        if (startDate != null)
            seasonal.put("startDate", startDate.toString());
        if (endDate != null)
            seasonal.put("endDate", endDate.toString());
    }

    private boolean readCatalogAvailability(ObjectNode item, Boolean eventAvailability) {
        if (eventAvailability != null)
            return Boolean.TRUE.equals(eventAvailability);
        JsonNode schedule = item.get("_schedule");
        return schedule == null || schedule.path("catalogAvailability").asBoolean(true);
    }

    private void markProcessed(MenuItemChangedEvent event) {
        processedEventRepository.save(event.getEventId(), event.getEventType(), clock.instant());
    }

    private boolean hasAnyWindows(Map<String, List<EventScheduleWindow>> windows) {
        return windows != null && windows.values().stream().anyMatch(day -> day != null && !day.isEmpty());
    }

}
