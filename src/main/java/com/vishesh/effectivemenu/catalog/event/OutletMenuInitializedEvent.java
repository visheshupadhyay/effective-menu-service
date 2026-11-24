package com.vishesh.effectivemenu.catalog.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Complete, one-time starting state for a newly activated outlet.
 *
 * <p>Unlike a normal menu-item change, this event gives Effective Menu every
 * item needed to create the outlet's first snapshot without querying Catalog.
 */
public final class OutletMenuInitializedEvent {
    private final UUID eventId;
    private final Long outletId;
    private final Long restaurantChainId;
    private final Long menuGroupId;
    private final Long outletVersion;
    private final String outletTimezone;
    private final Instant occurredAt;
    private final String correlationId;
    private final List<OutletMenuItemSnapshot> menuItems;

    public OutletMenuInitializedEvent(
            UUID eventId,
            Long outletId,
            Long restaurantChainId,
            Long menuGroupId,
            Long outletVersion,
            String outletTimezone,
            Instant occurredAt,
            String correlationId,
            List<OutletMenuItemSnapshot> menuItems) {
        if (eventId == null) {
            throw new IllegalArgumentException("Event ID is required");
        }
        validatePositive(outletId, "Outlet ID");
        validatePositive(restaurantChainId, "Restaurant chain ID");
        validatePositive(menuGroupId, "Menu group ID");
        if (outletVersion == null || outletVersion < 0) {
            throw new IllegalArgumentException("Outlet version must be zero or greater");
        }
        if (outletTimezone == null || outletTimezone.isBlank()) {
            throw new IllegalArgumentException("Outlet timezone is required");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("Occurred at is required");
        }
        if (menuItems == null) {
            throw new IllegalArgumentException("Complete menu items are required");
        }

        this.eventId = eventId;
        this.outletId = outletId;
        this.restaurantChainId = restaurantChainId;
        this.menuGroupId = menuGroupId;
        this.outletVersion = outletVersion;
        this.outletTimezone = outletTimezone.trim();
        this.occurredAt = occurredAt;
        this.correlationId = correlationId == null ? null : correlationId.trim();
        this.menuItems = List.copyOf(menuItems);
    }

    private static void validatePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    public UUID getEventId() {
        return eventId;
    }

    public CatalogEventType getEventType() {
        return CatalogEventType.OUTLET_MENU_INITIALIZED;
    }

    public Long getOutletId() {
        return outletId;
    }

    public Long getRestaurantChainId() {
        return restaurantChainId;
    }

    public Long getMenuGroupId() {
        return menuGroupId;
    }

    public Long getOutletVersion() {
        return outletVersion;
    }

    public String getOutletTimezone() {
        return outletTimezone;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public List<OutletMenuItemSnapshot> getMenuItems() {
        return menuItems;
    }
}
