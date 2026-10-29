package com.vishesh.effectivemenu.catalog.event;

import com.vishesh.effectivemenu.catalog.event.MenuItemStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
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

    private MenuItemChangedEvent event(UUID eventId, CatalogEventType eventType, Long itemId,
            Long chainId, Long outletId, Long version, String name, BigDecimal price,
            Boolean availability, Boolean visibility) {
        return new MenuItemChangedEvent(eventId, null, eventType, itemId, chainId, outletId,
                version, name, price, availability, visibility, OCCURRED_AT, "corr-1", MenuItemStatus.ACTIVE);
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
