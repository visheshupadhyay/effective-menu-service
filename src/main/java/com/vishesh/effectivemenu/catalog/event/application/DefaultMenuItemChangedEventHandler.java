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
import com.vishesh.effectivemenu.catalog.event.CatalogEventType;
import com.vishesh.effectivemenu.catalog.event.MenuItemChangedEvent;
import com.vishesh.effectivemenu.catalog.event.MenuItemStatus;
import com.vishesh.effectivemenu.snapshot.application.EffectiveMenuSnapshotRepository;
import com.vishesh.effectivemenu.snapshot.application.EffectiveMenuItemSnapshotMapper;
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
    private final EffectiveMenuItemSnapshotMapper itemSnapshotMapper;

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
        this.itemSnapshotMapper = new EffectiveMenuItemSnapshotMapper();
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

        JsonNode copiedMenu = snapshot.getEffectiveMenu().deepCopy();
        if (!copiedMenu.isObject() || !copiedMenu.has("items") || !copiedMenu.get("items").isArray()) {
            throw new IllegalArgumentException("Effective menu must contain an items array");
        }

        JsonNode itemsNode = copiedMenu.get("items");

        ArrayNode items = (ArrayNode) itemsNode;

        Long itemId = event.getItemId();

        boolean activationEvent = event.getEventType() == CatalogEventType.MENU_ITEM_ACTIVATED;
        if (activationEvent) {
            validateActivationEvent(event);
        }

        ObjectNode item = findItem(items, itemId);
        boolean addedByCompleteSnapshot = false;

        if (item == null) {
            // Only complete snapshots may introduce an item that this outlet has
            // not seen yet. A sparse change cannot safely build a customer item.
            boolean bundleConfigurationEvent = Boolean.TRUE.equals(event.getBundleConfigurationChanged());
            if (!activationEvent && !bundleConfigurationEvent) {
                throw new IllegalArgumentException("Menu item is not present in the outlet snapshot");
            }
            item = items.addObject();
            item.put("id", itemId);
            addedByCompleteSnapshot = true;
        }

        long lastAppliedVersion = addedByCompleteSnapshot
                ? -1L
                : item.path("_catalogVersion").asLong(snapshot.getSnapshotVersion());
        if (event.getAggregateVersion() <= lastAppliedVersion) {
            metrics.recordStale();
            markProcessed(event);
            return;
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

            if (Boolean.TRUE.equals(event.getModifierConfigurationChanged())) {
                itemSnapshotMapper.replaceModifierGroups(item, event.getModifierGroups());
            }

            if (Boolean.TRUE.equals(event.getBundleConfigurationChanged())) {
                itemSnapshotMapper.replaceBundleStructure(item, event.getBundleSnapshot(),
                        event.getAvailability(), event.getVisibility());
            }

            if (Boolean.TRUE.equals(event.getScheduleChanged())) {
                Map<String, List<EventScheduleWindow>> effectiveWindows = scheduleResolver.resolve(
                        event.getBaseScheduleWindows(), event.getOutletScheduleWindows());
                if (effectiveWindows.values().stream().allMatch(List::isEmpty)
                        && !event.getScheduleWindows().isEmpty()) {
                    effectiveWindows = event.getScheduleWindows();
                }
                itemSnapshotMapper.storeSchedule(item, effectiveWindows, event.getScheduleTimezone(), event.getAvailability());
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
                itemSnapshotMapper.storeSeasonalAvailability(item, event.getSeasonalStartDate(), event.getSeasonalEndDate(),
                        event.getSeasonalTimezone());
                boolean catalogAvailable = itemSnapshotMapper.readCatalogAvailability(item, event.getAvailability());
                boolean seasonActive = seasonalEvaluator.isActive(event.getSeasonalStartDate(),
                        event.getSeasonalEndDate(), event.getSeasonalTimezone(), clock.instant());
                item.put("availability", catalogAvailable && seasonActive);
            } else if (!Boolean.TRUE.equals(event.getScheduleChanged()) && event.getAvailability() != null) {
                item.put("availability", event.getAvailability());
                itemSnapshotMapper.updateStoredCatalogAvailability(item, event.getAvailability());
                itemSnapshotMapper.updateStoredBundleAvailability(item, event.getAvailability());
            }

            if (event.getVisibility() != null) {
                item.put("visibility", event.getVisibility());
                itemSnapshotMapper.updateStoredBundleVisibility(item, event.getVisibility());
            }

            item.put("status", event.getStatus().name());
            if (event.getStatus() == MenuItemStatus.HIDDEN
                    || event.getStatus() == MenuItemStatus.DRAFT) {
                item.put("visibility", false);
            }

            // Aggregate versions belong to individual Catalog items, while a
            // snapshot holds many items. Keep the last version beside the
            // item so a newly added Burger Meal can accept version 2 even if
            // another item previously advanced the outlet snapshot to 10.
            item.put("_catalogVersion", event.getAggregateVersion());
        }

        Instant now = clock.instant();
        effectiveMenuSnapshotRepository.save(new EffectiveMenuSnapshot(
                snapshot.getOutletId(),
                Math.max(snapshot.getSnapshotVersion(), event.getAggregateVersion()),
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

    /**
     * Activation creates a new customer-facing item, so it must carry a
     * complete item snapshot rather than the nullable fields of a sparse update.
     */
    private void validateActivationEvent(MenuItemChangedEvent event) {
        if (event.getItemName() == null || event.getItemName().isBlank()) {
            throw new IllegalArgumentException("Activated menu item must include a name");
        }
        if (event.getPrice() == null) {
            throw new IllegalArgumentException("Activated menu item must include a price");
        }
        if (event.getAvailability() == null) {
            throw new IllegalArgumentException("Activated menu item must include availability");
        }
        if (event.getVisibility() == null) {
            throw new IllegalArgumentException("Activated menu item must include visibility");
        }
        if (event.getStatus() != MenuItemStatus.ACTIVE) {
            throw new IllegalArgumentException("Activated menu item status must be ACTIVE");
        }
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

    private void markProcessed(MenuItemChangedEvent event) {
        processedEventRepository.save(event.getEventId(), event.getEventType(), clock.instant());
    }

    private boolean hasAnyWindows(Map<String, List<EventScheduleWindow>> windows) {
        return windows != null && windows.values().stream().anyMatch(day -> day != null && !day.isEmpty());
    }

}
