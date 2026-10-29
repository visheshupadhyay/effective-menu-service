package com.vishesh.effectivemenu.catalog.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Consumer-side copy of the Catalog menu-item change contract. */
public final class MenuItemChangedEvent {
    private final UUID eventId;
    private final UUID originalEventId;
    private final CatalogEventType eventType;
    private final Long itemId;
    private final Long restaurantChainId;
    private final Long outletId;
    private final Long aggregateVersion;
    private final Instant occurredAt;
    private final String itemName;
    private final BigDecimal price;
    private final Boolean availability;
    private final Boolean visibility;
    private final String correlationId;
    private final MenuItemStatus status;

    public MenuItemChangedEvent(UUID eventId, UUID originalEventId, CatalogEventType eventType,
            Long itemId, Long restaurantChainId, Long outletId, Long aggregateVersion,
            String itemName, BigDecimal price, Boolean availability, Boolean visibility,
            Instant occurredAt, String correlationId, MenuItemStatus status) {

        if (itemName == null && price == null && visibility == null && availability == null
                && status == null) {
            throw new IllegalArgumentException("At least one menu-item field must be supplied");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("Event ID cannot be null");
        }
        if (eventType == null) {
            throw new IllegalArgumentException("Event type cannot be null");
        }
        if (itemId == null || itemId <= 0) {
            throw new IllegalArgumentException("Item Id must be positive");
        }
        if (restaurantChainId == null || restaurantChainId <= 0) {
            throw new IllegalArgumentException("Restaurant Chain Id must be positive");
        }
        if (aggregateVersion == null || aggregateVersion < 0) {
            throw new IllegalArgumentException("Aggregate version must be non-negative");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("Occurred at cannot be null");
        }
        if (outletId != null && outletId <= 0) {
            throw new IllegalArgumentException("Outlet ID must be positive");
        }
        if (itemName != null && itemName.isBlank()) {
            throw new IllegalArgumentException("Item name cannot be blank");
        }
        if (price != null && price.signum() < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }

        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        this.eventId = eventId;
        this.originalEventId = originalEventId;
        this.eventType = eventType;
        this.itemId = itemId;
        this.restaurantChainId = restaurantChainId;
        this.outletId = outletId;
        this.aggregateVersion = aggregateVersion;
        this.itemName = itemName;
        this.price = price;
        this.availability = availability;
        this.visibility = visibility;
        this.occurredAt = occurredAt;
        this.correlationId = correlationId;
        this.status = status;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getOriginalEventId() {
        return originalEventId; 
    }

    public CatalogEventType getEventType() {
        return eventType;
    }

    public Long getItemId() {
        return itemId;
    }

    public Long getRestaurantChainId() {
        return restaurantChainId;
    }

    public Long getOutletId() { return outletId; }

    public Long getAggregateVersion() {
        return aggregateVersion;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getItemName() { return itemName; }

    public Boolean getAvailability() {
        return availability;
    }

    public Boolean getVisibility() {
        return visibility;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public MenuItemStatus getStatus() {
        return status; 
    }
}
