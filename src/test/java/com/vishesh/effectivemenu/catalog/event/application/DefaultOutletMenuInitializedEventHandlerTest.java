package com.vishesh.effectivemenu.catalog.event.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vishesh.effectivemenu.catalog.event.CatalogEventType;
import com.vishesh.effectivemenu.catalog.event.EventBundleChild;
import com.vishesh.effectivemenu.catalog.event.EventBundleSnapshot;
import com.vishesh.effectivemenu.catalog.event.EventModifierGroup;
import com.vishesh.effectivemenu.catalog.event.EventModifierOption;
import com.vishesh.effectivemenu.catalog.event.EventScheduleWindow;
import com.vishesh.effectivemenu.catalog.event.MenuItemKind;
import com.vishesh.effectivemenu.catalog.event.MenuItemStatus;
import com.vishesh.effectivemenu.catalog.event.OutletMenuInitializedEvent;
import com.vishesh.effectivemenu.catalog.event.OutletMenuItemSnapshot;
import com.vishesh.effectivemenu.monitoring.EffectiveMenuOperationalMetrics;
import com.vishesh.effectivemenu.snapshot.application.EffectiveMenuSnapshotRepository;
import com.vishesh.effectivemenu.snapshot.cache.EffectiveMenuCache;
import com.vishesh.effectivemenu.snapshot.domain.EffectiveMenuSnapshot;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
class DefaultOutletMenuInitializedEventHandlerTest {
    private static final Instant NOW = Instant.parse("2026-01-05T10:00:00Z");

    @Mock
    private EffectiveMenuSnapshotRepository snapshots;
    @Mock
    private ProcessedEventRepository processedEvents;
    @Mock
    private EffectiveMenuOperationalMetrics metrics;
    @Mock
    private EffectiveMenuCache cache;

    private DefaultOutletMenuInitializedEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DefaultOutletMenuInitializedEventHandler(
                snapshots, processedEvents, metrics, cache, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void newOutlet_createsCompleteSnapshotWithItemVersionsAndConfiguration() {
        OutletMenuInitializedEvent event = completeEvent();
        when(processedEvents.exists(event.getEventId())).thenReturn(false);
        when(snapshots.findByOutletId(501L)).thenReturn(Optional.empty());

        handler.handle(event);

        EffectiveMenuSnapshot saved = capturedSnapshot();
        assertEquals(501L, saved.getOutletId());
        assertEquals(3L, saved.getSnapshotVersion());
        ArrayNode items = (ArrayNode) saved.getEffectiveMenu().get("items");
        assertEquals(2, items.size());

        ObjectNode burger = (ObjectNode) items.get(0);
        assertEquals(8L, burger.path("_catalogVersion").asLong());
        assertEquals("Cheese", burger.get("modifierGroups").get(0).get("options").get(0)
                .get("optionName").asString());
        assertFalse(burger.get("_schedule").isMissingNode());
        assertEquals("2026-01-01", burger.get("_seasonalAvailability").get("startDate").asString());

        ObjectNode meal = (ObjectNode) items.get(1);
        assertEquals(2L, meal.path("_catalogVersion").asLong());
        assertEquals("Fries", meal.get("bundleChildren").get(0).get("name").asString());

        verify(cache).evict(501L);
        verify(processedEvents).save(event.getEventId(), CatalogEventType.OUTLET_MENU_INITIALIZED, NOW);
        verify(metrics).recordProcessed(event.getOccurredAt());
    }

    @Test
    void duplicateEvent_doesNotReadOrSaveSnapshotOrEvictCache() {
        OutletMenuInitializedEvent event = completeEvent();
        when(processedEvents.exists(event.getEventId())).thenReturn(true);

        handler.handle(event);

        verifyNoInteractions(snapshots, cache);
        verify(metrics).recordDuplicate();
    }

    @Test
    void lateDifferentInitialization_doesNotOverwriteExistingSnapshot() {
        OutletMenuInitializedEvent event = completeEvent();
        when(processedEvents.exists(event.getEventId())).thenReturn(false);
        when(snapshots.findByOutletId(501L)).thenReturn(Optional.of(existingSnapshot()));

        handler.handle(event);

        verify(snapshots, never()).save(any());
        verifyNoInteractions(cache);
        verify(processedEvents).save(eq(event.getEventId()), eq(CatalogEventType.OUTLET_MENU_INITIALIZED), any());
        verify(metrics).recordStale();
    }

    private EffectiveMenuSnapshot capturedSnapshot() {
        ArgumentCaptor<EffectiveMenuSnapshot> captor = ArgumentCaptor.forClass(EffectiveMenuSnapshot.class);
        verify(snapshots).save(captor.capture());
        return captor.getValue();
    }

    private OutletMenuInitializedEvent completeEvent() {
        EventModifierGroup extras = new EventModifierGroup(5L, "Choose Extras", 0, 1, 0,
                List.of(new EventModifierOption(11L, "Cheese", new BigDecimal("20.00"), 0)));
        OutletMenuItemSnapshot burger = new OutletMenuItemSnapshot(
                101L, 8L, "Burger", new BigDecimal("149.00"), MenuItemStatus.ACTIVE, true, true,
                Map.of("MONDAY", List.of(new EventScheduleWindow(LocalTime.of(9, 0), LocalTime.of(22, 0)))),
                Map.of(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), List.of(extras), null);

        EventBundleChild fries = new EventBundleChild(102L, "Fries", MenuItemKind.PRODUCT,
                1, 0, MenuItemStatus.ACTIVE, true, true, List.of());
        OutletMenuItemSnapshot meal = new OutletMenuItemSnapshot(
                150L, 2L, "Burger Meal", new BigDecimal("299.00"), MenuItemStatus.ACTIVE, true, true,
                Map.of(), Map.of(), null, null, List.of(),
                new EventBundleSnapshot(150L, "Burger Meal", new BigDecimal("299.00"), List.of(fries)));

        return new OutletMenuInitializedEvent(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                501L, 20L, 30L, 3L, "UTC", NOW, "corr-1", List.of(burger, meal));
    }

    private EffectiveMenuSnapshot existingSnapshot() {
        ObjectNode menu = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        menu.putArray("items");
        return new EffectiveMenuSnapshot(501L, 9L, menu, NOW, NOW);
    }
}
