package com.vishesh.effectivemenu.catalog.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class OutletMenuInitializedEventTest {
    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant OCCURRED_AT = Instant.parse("2026-01-05T10:00:00Z");

    @Test
    void missingOutletId_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> new OutletMenuInitializedEvent(
                EVENT_ID, null, 20L, 30L, 0L, "UTC", OCCURRED_AT, "corr-1", List.of(item(4L))));
    }

    @Test
    void negativeItemVersion_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> item(-1L));
    }

    @Test
    void completeValidEvent_isAccepted() {
        OutletMenuInitializedEvent event = new OutletMenuInitializedEvent(
                EVENT_ID, 501L, 20L, 30L, 3L, "Asia/Kolkata", OCCURRED_AT, "corr-1", List.of(item(4L)));

        assertEquals(CatalogEventType.OUTLET_MENU_INITIALIZED, event.getEventType());
        assertEquals(501L, event.getOutletId());
        assertEquals(3L, event.getOutletVersion());
        assertEquals(4L, event.getMenuItems().getFirst().menuItemVersion());
    }

    private OutletMenuItemSnapshot item(long version) {
        return new OutletMenuItemSnapshot(
                101L, version, "Burger", new BigDecimal("149.00"), MenuItemStatus.ACTIVE,
                true, true, Map.of(), Map.of(), null, null, List.of(), null);
    }
}
