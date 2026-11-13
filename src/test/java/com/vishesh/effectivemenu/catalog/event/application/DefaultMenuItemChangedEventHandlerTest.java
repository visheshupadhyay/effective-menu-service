package com.vishesh.effectivemenu.catalog.event.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.LocalTime;
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
import com.vishesh.effectivemenu.catalog.event.EventScheduleWindow;
import com.vishesh.effectivemenu.catalog.event.MenuItemChangedEvent;
import com.vishesh.effectivemenu.catalog.event.MenuItemStatus;
import com.vishesh.effectivemenu.snapshot.application.EffectiveMenuSnapshotRepository;
import com.vishesh.effectivemenu.snapshot.cache.EffectiveMenuCache;
import com.vishesh.effectivemenu.snapshot.domain.EffectiveMenuSnapshot;
import com.vishesh.effectivemenu.monitoring.EffectiveMenuOperationalMetrics;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
class DefaultMenuItemChangedEventHandlerTest {
    private static final Instant TIME = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant MONDAY_TEN_UTC = Instant.parse("2026-01-05T10:00:00Z");

    @Mock
    EffectiveMenuSnapshotRepository snapshots;
    @Mock
    ProcessedEventRepository processedEvents;
    @Mock
    EffectiveMenuOperationalMetrics metrics;
    @Mock
    EffectiveMenuCache cache;
    private DefaultMenuItemChangedEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DefaultMenuItemChangedEventHandler(snapshots, processedEvents, metrics, cache,
                Clock.fixed(MONDAY_TEN_UTC, ZoneOffset.UTC));
    }

    @Test
    void priceOnlyEvent_changesPriceAndPreservesOtherValues() {
        MenuItemChangedEvent event = event(5L, new BigDecimal("12.00"), null, null);
        when(processedEvents.exists(event.getEventId())).thenReturn(false);
        when(snapshots.findByOutletId(501L)).thenReturn(Optional.of(snapshot(4L)));

        handler.handle(event);

        EffectiveMenuSnapshot saved = captureSavedSnapshot();
        ObjectNode item = (ObjectNode) saved.getEffectiveMenu().get("items").get(0);
        assertEquals(12, item.get("price").asInt());
        assertEquals(true, item.get("availability").asBoolean());
        assertEquals(true, item.get("visibility").asBoolean());
        assertEquals(5L, saved.getSnapshotVersion());
        verify(metrics).recordProcessed(event.getOccurredAt());
        verify(cache).evict(event.getOutletId());
    }

    @Test
    void availabilityOnlyEvent_changesAvailabilityAndPreservesOtherValues() {
        MenuItemChangedEvent event = event(5L, null, false, null);
        when(processedEvents.exists(event.getEventId())).thenReturn(false);
        when(snapshots.findByOutletId(501L)).thenReturn(Optional.of(snapshot(4L)));

        handler.handle(event);

        EffectiveMenuSnapshot saved = captureSavedSnapshot();
        ObjectNode item = (ObjectNode) saved.getEffectiveMenu().get("items").get(0);
        assertEquals(10, item.get("price").asInt());
        assertEquals(false, item.get("availability").asBoolean());
        assertEquals(true, item.get("visibility").asBoolean());
        verify(cache).evict(event.getOutletId());
    }

    @Test
    void visibilityOnlyEvent_changesVisibilityAndPreservesOtherValues() {
        MenuItemChangedEvent event = event(5L, null, null, false);
        when(processedEvents.exists(event.getEventId())).thenReturn(false);
        when(snapshots.findByOutletId(501L)).thenReturn(Optional.of(snapshot(4L)));

        handler.handle(event);

        EffectiveMenuSnapshot saved = captureSavedSnapshot();
        ObjectNode item = (ObjectNode) saved.getEffectiveMenu().get("items").get(0);
        assertEquals(10, item.get("price").asInt());
        assertEquals(true, item.get("availability").asBoolean());
        assertEquals(false, item.get("visibility").asBoolean());
        verify(cache).evict(event.getOutletId());
    }

    @Test
    void hiddenEvent_keepsItemButMakesItInvisible() {
        MenuItemChangedEvent event = event(5L, new BigDecimal("12.00"), true, true, MenuItemStatus.HIDDEN);
        when(processedEvents.exists(event.getEventId())).thenReturn(false);
        when(snapshots.findByOutletId(501L)).thenReturn(Optional.of(snapshot(4L)));

        handler.handle(event);

        ObjectNode item = (ObjectNode) captureSavedSnapshot().getEffectiveMenu().get("items").get(0);
        assertEquals("HIDDEN", item.get("status").asText());
        assertEquals(false, item.get("visibility").asBoolean());
        verify(cache).evict(event.getOutletId());
    }

    @Test
    void archivedEvent_removesItemFromSnapshot() {
        MenuItemChangedEvent event = event(5L, null, null, null, MenuItemStatus.ARCHIVED);
        when(processedEvents.exists(event.getEventId())).thenReturn(false);
        when(snapshots.findByOutletId(501L)).thenReturn(Optional.of(snapshot(4L)));

        handler.handle(event);

        EffectiveMenuSnapshot saved = captureSavedSnapshot();
        assertEquals(0, saved.getEffectiveMenu().get("items").size());
        verify(cache).evict(event.getOutletId());
    }

    @Test
    void draftEvent_keepsItemButMakesItInvisible() {
        MenuItemChangedEvent event = event(5L, null, true, true, MenuItemStatus.DRAFT);
        when(processedEvents.exists(event.getEventId())).thenReturn(false);
        when(snapshots.findByOutletId(501L)).thenReturn(Optional.of(snapshot(4L)));

        handler.handle(event);

        ObjectNode item = (ObjectNode) captureSavedSnapshot().getEffectiveMenu().get("items").get(0);
        assertEquals("DRAFT", item.get("status").asText());
        assertEquals(false, item.get("visibility").asBoolean());
        verify(cache).evict(event.getOutletId());
    }

    @Test
    void duplicateEvent_isIgnored() {
        MenuItemChangedEvent event = event(5L, new BigDecimal("12.00"), null, null);
        when(processedEvents.exists(event.getEventId())).thenReturn(true);

        handler.handle(event);

        verify(snapshots, never()).findByOutletId(any());
        verify(snapshots, never()).save(any());
        verifyNoInteractions(cache);
        verify(metrics).recordDuplicate();
    }

    @Test
    void eventWithoutOutletId_isRejectedBeforeRepositoryAccess() {
        MenuItemChangedEvent event = new MenuItemChangedEvent(
                UUID.randomUUID(), null, CatalogEventType.MENU_ITEM_CHANGED,
                101L, 20L, null, 5L,
                null, new BigDecimal("12.00"), null, null, TIME, "corr-1", MenuItemStatus.ACTIVE);

        assertThrows(IllegalArgumentException.class, () -> handler.handle(event));

        verifyNoInteractions(snapshots, processedEvents, cache);
    }

    @Test
    void staleEvent_isMarkedProcessedWithoutSaving() {
        MenuItemChangedEvent event = event(4L, new BigDecimal("12.00"), null, null);
        when(processedEvents.exists(event.getEventId())).thenReturn(false);
        when(snapshots.findByOutletId(501L)).thenReturn(Optional.of(snapshot(4L)));

        handler.handle(event);

        verify(snapshots, never()).save(any());
        verifyNoInteractions(cache);
        verify(processedEvents).save(
                eq(event.getEventId()),
                eq(CatalogEventType.MENU_ITEM_CHANGED),
                any(Instant.class));
        verify(metrics).recordStale();
    }

    @Test
    void missingSnapshot_isRejected() {
        MenuItemChangedEvent event = event(5L, new BigDecimal("12.00"), null, null);
        when(processedEvents.exists(event.getEventId())).thenReturn(false);
        when(snapshots.findByOutletId(501L)).thenReturn(Optional.empty());

        assertThrows(EffectiveMenuSnapshotNotFoundException.class, () -> handler.handle(event));
        verify(snapshots, never()).save(any());
        verifyNoInteractions(cache);
    }

    @Test
    void eventForAnotherOutlet_cannotChangeBerlinLocalItem() {
        MenuItemChangedEvent munichEvent = event(
                5L, 900L, 502L, new BigDecimal("15.00"), null, null);

        when(processedEvents.exists(munichEvent.getEventId())).thenReturn(false);
        // Munich has its own snapshot, but it does not contain Berlin's local item 900.
        when(snapshots.findByOutletId(502L))
                .thenReturn(Optional.of(snapshot(502L, 4L, 901L, "Munich Item")));

        assertThrows(IllegalArgumentException.class,
                () -> handler.handle(munichEvent));

        verify(snapshots).findByOutletId(502L);
        verify(snapshots, never()).findByOutletId(501L);
        verify(snapshots, never()).save(any());
    }

    @Test
    void eventForItemAbsentFromSnapshot_isRejectedWithoutRecreatingItem() {
        MenuItemChangedEvent archivedItemEvent = event(
                5L, 999L, 501L, new BigDecimal("12.00"), true, true);

        when(processedEvents.exists(archivedItemEvent.getEventId())).thenReturn(false);
        when(snapshots.findByOutletId(501L))
                .thenReturn(Optional.of(snapshot(4L)));

        assertThrows(IllegalArgumentException.class,
                () -> handler.handle(archivedItemEvent));

        verify(snapshots, never()).save(any());
        verify(processedEvents, never()).save(any(), any(), any());
    }

    @Test
    void activeSchedule_keepsItemAvailableAndEvictsOutletCache() {
        MenuItemChangedEvent event = scheduleEvent(5L, "UTC", Map.of(
                "MONDAY", List.of(new EventScheduleWindow(LocalTime.of(9, 0), LocalTime.of(11, 0)))));
        when(processedEvents.exists(event.getEventId())).thenReturn(false);
        when(snapshots.findByOutletId(501L)).thenReturn(Optional.of(snapshot(4L)));

        handler.handle(event);

        EffectiveMenuSnapshot saved = captureSavedSnapshot();
        ObjectNode item = (ObjectNode) saved.getEffectiveMenu().get("items").get(0);
        assertEquals(true, item.get("availability").asBoolean());
        assertEquals(true, item.get("visibility").asBoolean());
        assertEquals(5L, saved.getSnapshotVersion());
        verify(cache).evict(501L);
    }

    @Test
    void inactiveSchedule_marksItemUnavailableButPreservesVisibility() {
        MenuItemChangedEvent event = scheduleEvent(5L, "UTC", Map.of(
                "MONDAY", List.of(new EventScheduleWindow(LocalTime.of(11, 0), LocalTime.of(12, 0)))));
        when(processedEvents.exists(event.getEventId())).thenReturn(false);
        when(snapshots.findByOutletId(501L)).thenReturn(Optional.of(snapshot(4L)));

        handler.handle(event);

        ObjectNode item = (ObjectNode) captureSavedSnapshot().getEffectiveMenu().get("items").get(0);
        assertEquals(false, item.get("availability").asBoolean());
        assertEquals(true, item.get("visibility").asBoolean());
        verify(cache).evict(501L);
    }

    @Test
    void emptySchedule_preservesExistingAvailabilityAndVisibility() {
        MenuItemChangedEvent event = scheduleEvent(5L, "UTC", Map.of());
        when(processedEvents.exists(event.getEventId())).thenReturn(false);
        when(snapshots.findByOutletId(501L)).thenReturn(Optional.of(snapshot(4L)));

        handler.handle(event);

        ObjectNode item = (ObjectNode) captureSavedSnapshot().getEffectiveMenu().get("items").get(0);
        assertEquals(true, item.get("availability").asBoolean());
        assertEquals(true, item.get("visibility").asBoolean());
        verify(cache).evict(501L);
    }

    @Test
    void scheduleUsesOutletTimezoneWhenEvaluatingWindow() {
        MenuItemChangedEvent event = scheduleEvent(5L, "America/New_York", Map.of(
                "MONDAY", List.of(new EventScheduleWindow(LocalTime.of(5, 0), LocalTime.of(6, 0)))));
        when(processedEvents.exists(event.getEventId())).thenReturn(false);
        when(snapshots.findByOutletId(501L)).thenReturn(Optional.of(snapshot(4L)));

        handler.handle(event);

        ObjectNode item = (ObjectNode) captureSavedSnapshot().getEffectiveMenu().get("items").get(0);
        assertEquals(true, item.get("availability").asBoolean());
    }

    @Test
    void outletScheduleOverrideWinsForMondayWhileTuesdayInheritsBase() {
        MenuItemChangedEvent event = new MenuItemChangedEvent(
                UUID.randomUUID(), null, CatalogEventType.MENU_ITEM_CHANGED,
                101L, 20L, 501L, 5L, null, null, true, true, TIME, "corr-1", MenuItemStatus.ACTIVE,
                true, 5L, "UTC",
                Map.of(),
                Map.of("MONDAY", List.of(new EventScheduleWindow(LocalTime.of(9, 0), LocalTime.of(11, 0))),
                        "TUESDAY", List.of(new EventScheduleWindow(LocalTime.of(9, 0), LocalTime.of(12, 0)))),
                Map.of("MONDAY", List.of(new EventScheduleWindow(LocalTime.of(17, 0), LocalTime.of(20, 0)))));
        when(processedEvents.exists(event.getEventId())).thenReturn(false);
        when(snapshots.findByOutletId(501L)).thenReturn(Optional.of(snapshot(4L)));

        handler.handle(event);

        ObjectNode item = (ObjectNode) captureSavedSnapshot().getEffectiveMenu().get("items").get(0);
        // Monday 10:00 is inside the base window, not the outlet override.
        assertEquals(false, item.get("availability").asBoolean());
        assertEquals(true, item.get("visibility").asBoolean());
        verify(cache).evict(501L);
    }

    @Test
    void archivedScheduleEvent_removesItemRegardlessOfSchedule() {
        MenuItemChangedEvent event = scheduleEvent(5L, "UTC", Map.of(
                "MONDAY", List.of(new EventScheduleWindow(LocalTime.of(9, 0), LocalTime.of(11, 0)))),
                MenuItemStatus.ARCHIVED);
        when(processedEvents.exists(event.getEventId())).thenReturn(false);
        when(snapshots.findByOutletId(501L)).thenReturn(Optional.of(snapshot(4L)));

        handler.handle(event);

        assertEquals(0, captureSavedSnapshot().getEffectiveMenu().get("items").size());
        verify(cache).evict(501L);
    }

    @Test
    void staleScheduleEvent_isIgnoredWithoutCacheEviction() {
        MenuItemChangedEvent event = scheduleEvent(4L, "UTC", Map.of(
                "MONDAY", List.of(new EventScheduleWindow(LocalTime.of(9, 0), LocalTime.of(11, 0)))));
        when(processedEvents.exists(event.getEventId())).thenReturn(false);
        when(snapshots.findByOutletId(501L)).thenReturn(Optional.of(snapshot(4L)));

        handler.handle(event);

        verify(snapshots, never()).save(any());
        verifyNoInteractions(cache);
        verify(metrics).recordStale();
    }

    @Test
    void duplicateScheduleEvent_isIgnoredBeforeReadingSnapshot() {
        MenuItemChangedEvent event = scheduleEvent(5L, "UTC", Map.of());
        when(processedEvents.exists(event.getEventId())).thenReturn(true);

        handler.handle(event);

        verify(snapshots, never()).findByOutletId(any());
        verify(snapshots, never()).save(any());
        verifyNoInteractions(cache);
        verify(metrics).recordDuplicate();
    }


    private EffectiveMenuSnapshot captureSavedSnapshot() {
        ArgumentCaptor<EffectiveMenuSnapshot> captor = ArgumentCaptor.forClass(EffectiveMenuSnapshot.class);
        verify(snapshots).save(captor.capture());
        return captor.getValue();
    }

    private EffectiveMenuSnapshot snapshot(long version) {
        return snapshot(501L, version, 101L, "Burger");
    }

    private EffectiveMenuSnapshot snapshot(long outletId, long version, long itemId, String name) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode menu = mapper.createObjectNode();
        ObjectNode item = mapper.createObjectNode();
        item.put("id", itemId);
        item.put("name", name);
        item.put("price", 10.00);
        item.put("availability", true);
        item.put("visibility", true);
        menu.set("items", mapper.createArrayNode().add(item));
        return new EffectiveMenuSnapshot(outletId, version, menu, TIME, TIME);
    }

    private MenuItemChangedEvent event(long version, BigDecimal price,
            Boolean availability, Boolean visibility) {
        return event(version, 101L, 501L, price, availability, visibility, MenuItemStatus.ACTIVE);
    }

    private MenuItemChangedEvent event(long version, BigDecimal price,
            Boolean availability, Boolean visibility, MenuItemStatus status) {
        return event(version, 101L, 501L, price, availability, visibility, status);
    }

    private MenuItemChangedEvent event(long version, long itemId, long outletId,
            BigDecimal price, Boolean availability, Boolean visibility) {
        return event(version, itemId, outletId, price, availability, visibility, MenuItemStatus.ACTIVE);
    }

    private MenuItemChangedEvent event(long version, long itemId, long outletId,
            BigDecimal price, Boolean availability, Boolean visibility, MenuItemStatus status) {
        return new MenuItemChangedEvent(UUID.randomUUID(), null,
                CatalogEventType.MENU_ITEM_CHANGED, itemId, 20L, outletId, version,
                null, price, availability, visibility, TIME, "corr-1", status);
    }

    private MenuItemChangedEvent scheduleEvent(long version, String timezone,
            Map<String, List<EventScheduleWindow>> windows) {
        return scheduleEvent(version, timezone, windows, MenuItemStatus.ACTIVE);
    }

    private MenuItemChangedEvent scheduleEvent(long version, String timezone,
            Map<String, List<EventScheduleWindow>> windows, MenuItemStatus status) {
        return new MenuItemChangedEvent(UUID.randomUUID(), null,
                CatalogEventType.MENU_ITEM_CHANGED, 101L, 20L, 501L, version,
                null, null, true, true, TIME, "corr-1", status,
                true, version, timezone, windows);
    }
}
