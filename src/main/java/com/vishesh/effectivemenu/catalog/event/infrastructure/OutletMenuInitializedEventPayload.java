package com.vishesh.effectivemenu.catalog.event.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.vishesh.effectivemenu.catalog.event.CatalogEventType;
import com.vishesh.effectivemenu.catalog.event.OutletMenuItemSnapshot;

/**
 * JSON-shaped Kafka input for an outlet's one-time complete starting menu.
 *
 * <p>This class has no business rules. The consumer converts it into the
 * validated {@code OutletMenuInitializedEvent} before application code uses it.
 */
public class OutletMenuInitializedEventPayload {
    private UUID eventId;
    private CatalogEventType eventType;
    private Long outletId;
    private Long restaurantChainId;
    private Long menuGroupId;
    private Long outletVersion;
    private String outletTimezone;
    private Instant occurredAt;
    private String correlationId;
    private List<OutletMenuItemSnapshot> menuItems;

    public OutletMenuInitializedEventPayload() {
        // Required by the JSON deserializer.
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public CatalogEventType getEventType() {
        return eventType;
    }

    public void setEventType(CatalogEventType eventType) {
        this.eventType = eventType;
    }

    public Long getOutletId() {
        return outletId;
    }

    public void setOutletId(Long outletId) {
        this.outletId = outletId;
    }

    public Long getRestaurantChainId() {
        return restaurantChainId;
    }

    public void setRestaurantChainId(Long restaurantChainId) {
        this.restaurantChainId = restaurantChainId;
    }

    public Long getMenuGroupId() {
        return menuGroupId;
    }

    public void setMenuGroupId(Long menuGroupId) {
        this.menuGroupId = menuGroupId;
    }

    public Long getOutletVersion() {
        return outletVersion;
    }

    public void setOutletVersion(Long outletVersion) {
        this.outletVersion = outletVersion;
    }

    public String getOutletTimezone() {
        return outletTimezone;
    }

    public void setOutletTimezone(String outletTimezone) {
        this.outletTimezone = outletTimezone;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public List<OutletMenuItemSnapshot> getMenuItems() {
        return menuItems;
    }

    public void setMenuItems(List<OutletMenuItemSnapshot> menuItems) {
        this.menuItems = menuItems;
    }
}
