package com.vishesh.effectivemenu.catalog.event;

import com.vishesh.effectivemenu.catalog.event.MenuItemStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class MenuItemChangedEventTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void validCompleteEvent_isAcceptedAndExposesAllValues() {
        UUID originalId = UUID.randomUUID();
        MenuItemChangedEvent event = validEvent(originalId);

        assertEquals(EVENT_ID, event.getEventId());
        assertEquals(originalId, event.getOriginalEventId());
        assertEquals(CatalogEventType.MENU_ITEM_CHANGED, event.getEventType());
        assertEquals(101L, event.getItemId());
        assertEquals(20L, event.getRestaurantChainId());
        assertEquals(501L, event.getOutletId());
        assertEquals(4L, event.getAggregateVersion());
        assertEquals("Burger", event.getItemName());
        assertEquals(new BigDecimal("12.00"), event.getPrice());
        assertEquals(Boolean.TRUE, event.getAvailability());
        assertEquals(Boolean.TRUE, event.getVisibility());
        assertEquals(OCCURRED_AT, event.getOccurredAt());
        assertEquals("corr-1", event.getCorrelationId());
    }

    @Test
    void invalidValues_areRejected() {
        assertThrows(IllegalArgumentException.class, () -> eventWithTimestamp(null));
        assertThrows(IllegalArgumentException.class, () -> event(EVENT_ID, null, 101L, 20L, 501L,
                4L, "Burger", new BigDecimal("12.00"), true, true));
        assertThrows(IllegalArgumentException.class, () -> validEvent(null, null, 0L, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> validEvent(null, null, null, 0L, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> validEvent(null, null, null, null, -1L, null, null));
        assertThrows(IllegalArgumentException.class, () -> validEvent(null, null, null, null, null, null, null, new BigDecimal("-1.00")));
        assertThrows(IllegalArgumentException.class, () -> event("", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> event(null, null, null, null,
                null, null, null, null, null, null));
    }

    @Test
    void priceOnlyChange_isAcceptedAndLeavesOtherChangesAbsent() {
        MenuItemChangedEvent event = event(null, new BigDecimal("12.00"), null, null);

        assertEquals(null, event.getItemName());
        assertEquals(new BigDecimal("12.00"), event.getPrice());
        assertEquals(null, event.getAvailability());
        assertEquals(null, event.getVisibility());
    }

    @Test
    void availabilityOnlyChange_isAccepted() {
        MenuItemChangedEvent event = event(null, null, false, null);

        assertEquals(null, event.getItemName());
        assertEquals(null, event.getPrice());
        assertEquals(Boolean.FALSE, event.getAvailability());
        assertEquals(null, event.getVisibility());
    }

    @Test
    void visibilityOnlyChange_isAccepted() {
        MenuItemChangedEvent event = event(null, null, null, false);

        assertEquals(null, event.getItemName());
        assertEquals(null, event.getPrice());
        assertEquals(null, event.getAvailability());
        assertEquals(Boolean.FALSE, event.getVisibility());
    }

    @Test
    void noChangedField_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> event(null, null, null, null));
    }

    @Test
    void negativeSuppliedPrice_isRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> event(null, new BigDecimal("-1.00"), null, null));
    }

    @Test
    void scheduleEvent_exposesBaseAndOutletWindows() {
        EventScheduleWindow baseWindow = new EventScheduleWindow(LocalTime.of(9, 0), LocalTime.of(12, 0));
        EventScheduleWindow outletWindow = new EventScheduleWindow(LocalTime.of(17, 0), LocalTime.of(20, 0));

        MenuItemChangedEvent event = new MenuItemChangedEvent(EVENT_ID, null,
                CatalogEventType.MENU_ITEM_CHANGED, 101L, 20L, 501L, 5L,
                null, null, true, true, OCCURRED_AT, "corr-1", MenuItemStatus.ACTIVE,
                true, 2L, "Europe/Berlin", Map.of(),
                Map.of("MONDAY", List.of(baseWindow)),
                Map.of("MONDAY", List.of(outletWindow)));

        assertEquals(List.of(baseWindow), event.getBaseScheduleWindows().get("MONDAY"));
        assertEquals(List.of(outletWindow), event.getOutletScheduleWindows().get("MONDAY"));
    }

    @Test
    void modifierConfigurationEvent_acceptsACompleteGroupSnapshot() {
        EventModifierOption cheese = new EventModifierOption(11L, "Cheese", new BigDecimal("20.00"), 0);
        EventModifierGroup extras = new EventModifierGroup(5L, "Choose Extras", 0, 1, 0, List.of(cheese));

        MenuItemChangedEvent event = modifierEvent(List.of(extras));

        assertEquals(CatalogEventType.MODIFIER_CONFIGURATION_CHANGED, event.getEventType());
        assertEquals(Boolean.TRUE, event.getModifierConfigurationChanged());
        assertEquals(List.of(extras), event.getModifierGroups());
    }

    @Test
    void modifierConfigurationEvent_acceptsAnEmptySnapshotToRemoveAllGroups() {
        MenuItemChangedEvent event = modifierEvent(List.of());

        assertEquals(Boolean.TRUE, event.getModifierConfigurationChanged());
        assertEquals(List.of(), event.getModifierGroups());
    }

    @Test
    void modifierConfigurationEvent_rejectsMissingGroupSnapshot() {
        assertThrows(IllegalArgumentException.class, () -> new MenuItemChangedEvent(
                EVENT_ID, null, CatalogEventType.MODIFIER_CONFIGURATION_CHANGED,
                101L, 20L, 501L, 5L, null, null, null, null, OCCURRED_AT, "corr-1", MenuItemStatus.ACTIVE,
                false, null, null, Map.of(), Map.of(), Map.of(), false, null, null, null, null, true, null));
    }

    @Test
    void oldPriceEvent_keepsModifierFieldsAtTheirSafeDefaults() {
        MenuItemChangedEvent event = event(null, new BigDecimal("12.00"), null, null);

        assertEquals(Boolean.FALSE, event.getModifierConfigurationChanged());
        assertEquals(List.of(), event.getModifierGroups());
    }

    @Test
    void bundleConfigurationEvent_acceptsACompleteReplacementSnapshot() {
        EventBundleSnapshot bundle = bundleSnapshot();

        MenuItemChangedEvent event = bundleEvent(bundle);

        assertEquals(CatalogEventType.BUNDLE_CONFIGURATION_CHANGED, event.getEventType());
        assertEquals(Boolean.TRUE, event.getBundleConfigurationChanged());
        assertEquals(bundle, event.getBundleSnapshot());
    }

    @Test
    void bundleConfigurationEvent_rejectsMissingSnapshot() {
        assertThrows(IllegalArgumentException.class, () -> new MenuItemChangedEvent(
                EVENT_ID, null, CatalogEventType.BUNDLE_CONFIGURATION_CHANGED,
                101L, 20L, 501L, 5L, null, null, null, null, OCCURRED_AT, "corr-1", MenuItemStatus.ACTIVE,
                false, null, null, Map.of(), Map.of(), Map.of(), false, null, null, null, null,
                false, List.of(), true, null));
    }

    private MenuItemChangedEvent event(UUID eventId, CatalogEventType eventType, Long itemId,
            Long chainId, Long outletId, Long version, String name, BigDecimal price,
            Boolean availability, Boolean visibility) {
        return new MenuItemChangedEvent(eventId, null, eventType, itemId, chainId, outletId,
                version, name, price, availability, visibility, OCCURRED_AT, "corr-1", MenuItemStatus.ACTIVE);
    }

    private MenuItemChangedEvent modifierEvent(List<EventModifierGroup> groups) {
        return new MenuItemChangedEvent(EVENT_ID, null, CatalogEventType.MODIFIER_CONFIGURATION_CHANGED,
                101L, 20L, 501L, 5L, null, null, null, null, OCCURRED_AT, "corr-1", MenuItemStatus.ACTIVE,
                false, null, null, Map.of(), Map.of(), Map.of(), false, null, null, null, null, true, groups);
    }

    private MenuItemChangedEvent bundleEvent(EventBundleSnapshot bundle) {
        return new MenuItemChangedEvent(EVENT_ID, null, CatalogEventType.BUNDLE_CONFIGURATION_CHANGED,
                101L, 20L, 501L, 5L, null, null, null, null, OCCURRED_AT, "corr-1", MenuItemStatus.ACTIVE,
                false, null, null, Map.of(), Map.of(), Map.of(), false, null, null, null, null,
                false, List.of(), true, bundle);
    }

    private EventBundleSnapshot bundleSnapshot() {
        EventBundleChild fries = new EventBundleChild(102L, "Fries", MenuItemKind.PRODUCT,
                1, 0, MenuItemStatus.ACTIVE, true, true, List.of());
        return new EventBundleSnapshot(101L, "Burger Meal", new BigDecimal("299.00"), List.of(fries));
    }

    private MenuItemChangedEvent event(String name, BigDecimal price,
            Boolean availability, Boolean visibility) {
        return new MenuItemChangedEvent(EVENT_ID, null, CatalogEventType.MENU_ITEM_CHANGED,
                101L, 20L, 501L, 4L, name, price, availability, visibility,
                OCCURRED_AT, "corr-1",
                name == null && price == null && availability == null && visibility == null
                        ? null : MenuItemStatus.ACTIVE);
    }

    private MenuItemChangedEvent eventWithTimestamp(Instant timestamp) {
        return new MenuItemChangedEvent(EVENT_ID, null, CatalogEventType.MENU_ITEM_CHANGED, 101L, 20L, 501L,
                4L, "Burger", new BigDecimal("12.00"), true, true, timestamp, "corr-1", MenuItemStatus.ACTIVE);
    }

    private MenuItemChangedEvent validEvent(UUID originalId) {
        return new MenuItemChangedEvent(EVENT_ID, originalId, CatalogEventType.MENU_ITEM_CHANGED, 101L, 20L,
                501L, 4L, "Burger", new BigDecimal("12.00"), true, true, OCCURRED_AT, "corr-1", MenuItemStatus.ACTIVE);
    }

    private MenuItemChangedEvent validEvent(UUID originalId, CatalogEventType type, Long itemId,
            Long chainId, Long version, String name, String ignored) {
        return validEvent(originalId, type, itemId, chainId, version, name, ignored, new BigDecimal("12.00"), true);
    }

    private MenuItemChangedEvent validEvent(UUID originalId, CatalogEventType type, Long itemId,
            Long chainId, Long version, String name, String ignored, BigDecimal price) {
        return validEvent(originalId, type, itemId, chainId, version, name, ignored, price, true);
    }

    private MenuItemChangedEvent validEvent(UUID originalId, CatalogEventType type, Long itemId,
            Long chainId, Long version, String name, String ignored, BigDecimal price, Boolean availability) {
        return new MenuItemChangedEvent(EVENT_ID, originalId,
                CatalogEventType.MENU_ITEM_CHANGED,
                itemId == null ? 101L : itemId,
                chainId == null ? 20L : chainId,
                501L,
                version == null ? 4L : version,
                name == null ? "Burger" : name,
                price,
                availability,
                true,
                OCCURRED_AT,
                "corr-1", MenuItemStatus.ACTIVE);
    }
}
