package com.vishesh.effectivemenu.catalog.event.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.vishesh.effectivemenu.catalog.event.CatalogEventType;
import com.vishesh.effectivemenu.catalog.event.EventScheduleWindow;
import com.vishesh.effectivemenu.catalog.event.MenuItemStatus;
import com.vishesh.effectivemenu.catalog.event.OutletMenuInitializedEvent;
import com.vishesh.effectivemenu.catalog.event.OutletMenuItemSnapshot;
import com.vishesh.effectivemenu.monitoring.EffectiveMenuOperationalMetrics;
import com.vishesh.effectivemenu.snapshot.application.EffectiveMenuItemSnapshotMapper;
import com.vishesh.effectivemenu.snapshot.application.EffectiveMenuSnapshotRepository;
import com.vishesh.effectivemenu.snapshot.cache.EffectiveMenuCache;
import com.vishesh.effectivemenu.snapshot.domain.EffectiveMenuSnapshot;
import com.vishesh.effectivemenu.snapshot.schedule.EffectiveScheduleResolver;
import com.vishesh.effectivemenu.snapshot.schedule.SeasonalAvailabilityEvaluator;
import com.vishesh.effectivemenu.snapshot.schedule.WeeklyScheduleEvaluator;

import jakarta.transaction.Transactional;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Creates the first stored menu for an outlet from one complete Catalog event.
 *
 * <p>Normal item-change events require an existing snapshot. This handler is
 * the only path that can create that first snapshot for an outlet.
 */
@Service("defaultOutletMenuInitializedEventHandler")
public class DefaultOutletMenuInitializedEventHandler implements OutletMenuInitializedEventHandler {
    private final EffectiveMenuSnapshotRepository snapshots;
    private final ProcessedEventRepository processedEvents;
    private final EffectiveMenuOperationalMetrics metrics;
    private final EffectiveMenuCache cache;
    private final Clock clock;
    private final WeeklyScheduleEvaluator scheduleEvaluator;
    private final EffectiveScheduleResolver scheduleResolver;
    private final SeasonalAvailabilityEvaluator seasonalEvaluator;
    private final EffectiveMenuItemSnapshotMapper itemMapper;

    @Autowired
    public DefaultOutletMenuInitializedEventHandler(
            @Qualifier("effectiveMenuSnapshotPersistenceAdapter") EffectiveMenuSnapshotRepository snapshots,
            @Qualifier("processedEventPersistenceAdapter") ProcessedEventRepository processedEvents,
            EffectiveMenuOperationalMetrics metrics,
            EffectiveMenuCache cache,
            WeeklyScheduleEvaluator scheduleEvaluator,
            EffectiveScheduleResolver scheduleResolver) {
        this(snapshots, processedEvents, metrics, cache, Clock.systemUTC(), scheduleEvaluator, scheduleResolver);
    }

    DefaultOutletMenuInitializedEventHandler(
            EffectiveMenuSnapshotRepository snapshots,
            ProcessedEventRepository processedEvents,
            EffectiveMenuOperationalMetrics metrics,
            EffectiveMenuCache cache,
            Clock clock) {
        this(snapshots, processedEvents, metrics, cache, clock,
                new WeeklyScheduleEvaluator(), new EffectiveScheduleResolver());
    }

    DefaultOutletMenuInitializedEventHandler(
            EffectiveMenuSnapshotRepository snapshots,
            ProcessedEventRepository processedEvents,
            EffectiveMenuOperationalMetrics metrics,
            EffectiveMenuCache cache,
            Clock clock,
            WeeklyScheduleEvaluator scheduleEvaluator,
            EffectiveScheduleResolver scheduleResolver) {
        if (snapshots == null || processedEvents == null || metrics == null || cache == null || clock == null
                || scheduleEvaluator == null || scheduleResolver == null) {
            throw new IllegalArgumentException("Outlet initialization handler dependencies are required");
        }
        this.snapshots = snapshots;
        this.processedEvents = processedEvents;
        this.metrics = metrics;
        this.cache = cache;
        this.clock = clock;
        this.scheduleEvaluator = scheduleEvaluator;
        this.scheduleResolver = scheduleResolver;
        this.seasonalEvaluator = new SeasonalAvailabilityEvaluator();
        this.itemMapper = new EffectiveMenuItemSnapshotMapper();
    }

    @Override
    @Transactional
    public void handle(OutletMenuInitializedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Outlet initialization event is required");
        }

        // Kafka can redeliver the exact same message. It must not create a
        // second snapshot or trigger another cache eviction.
        if (processedEvents.exists(event.getEventId())) {
            metrics.recordDuplicate();
            return;
        }

        // A differently identified initialization event may arrive late. The
        // existing snapshot could contain newer ordinary item updates, so it
        // must never be replaced by an older complete starting menu.
        if (snapshots.findByOutletId(event.getOutletId()).isPresent()) {
            processedEvents.save(event.getEventId(), CatalogEventType.OUTLET_MENU_INITIALIZED, clock.instant());
            metrics.recordStale();
            return;
        }

        ObjectNode menu = JsonNodeFactory.instance.objectNode();
        ArrayNode items = menu.putArray("items");
        for (OutletMenuItemSnapshot itemSnapshot : event.getMenuItems()) {
            addItem(items, itemSnapshot, event.getOutletTimezone());
        }

        Instant now = clock.instant();
        snapshots.save(new EffectiveMenuSnapshot(
                event.getOutletId(), event.getOutletVersion(), menu, now, now));
        cache.evict(event.getOutletId());
        processedEvents.save(event.getEventId(), CatalogEventType.OUTLET_MENU_INITIALIZED, now);
        metrics.recordProcessed(event.getOccurredAt());
    }

    private void addItem(ArrayNode items, OutletMenuItemSnapshot source, String outletTimezone) {
        // Catalog normally omits archived items. Keep this defensive check so
        // malformed/replayed input can never create an archived customer item.
        if (source.status() == MenuItemStatus.ARCHIVED) {
            return;
        }

        ObjectNode item = itemMapper.createItem(
                source.menuItemId(), source.itemName(), source.price(), source.availability(),
                source.visibility(), source.status(), source.menuItemVersion());

        itemMapper.replaceModifierGroups(item, source.modifierGroups());
        // Build the bundle before applying time rules. The mapper calculates
        // whether required children make the bundle unavailable; schedule and
        // seasonal checks below must only make that result stricter, never
        // accidentally make an unavailable bundle available again.
        if (source.bundleSnapshot() != null) {
            itemMapper.replaceBundleStructure(item, source.bundleSnapshot(), source.availability(), source.visibility());
        }

        Map<String, List<EventScheduleWindow>> effectiveWindows = scheduleResolver.resolve(
                source.baseScheduleWindows(), source.outletScheduleWindows());
        boolean hasSchedule = hasAnyWindows(effectiveWindows);
        if (hasSchedule) {
            itemMapper.storeSchedule(item, effectiveWindows, outletTimezone, source.availability());
            boolean scheduleActive = scheduleEvaluator.isActive(effectiveWindows, outletTimezone, clock.instant());
            item.put("availability", item.path("availability").asBoolean(false) && scheduleActive);
        }

        if (source.seasonalStartDate() != null) {
            itemMapper.storeSeasonalAvailability(item, source.seasonalStartDate(), source.seasonalEndDate(), outletTimezone);
            boolean seasonActive = seasonalEvaluator.isActive(source.seasonalStartDate(), source.seasonalEndDate(),
                    outletTimezone, clock.instant());
            item.put("availability", item.path("availability").asBoolean(true) && seasonActive);
        }

        if (source.status() == MenuItemStatus.HIDDEN || source.status() == MenuItemStatus.DRAFT) {
            item.put("visibility", false);
        }
        items.add(item);
    }

    private boolean hasAnyWindows(Map<String, List<EventScheduleWindow>> windows) {
        return windows != null && windows.values().stream().anyMatch(day -> day != null && !day.isEmpty());
    }
}
