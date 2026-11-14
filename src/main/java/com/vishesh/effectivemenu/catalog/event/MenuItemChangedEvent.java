package com.vishesh.effectivemenu.catalog.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;
import java.util.Map;

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
    private final Boolean scheduleChanged;
    private final Long scheduleVersion;
    private final String scheduleId;
    private final String scheduleTimezone;
    private final Map<String, List<EventScheduleWindow>> scheduleWindows;
    private final Map<String, List<EventScheduleWindow>> baseScheduleWindows;
    private final Map<String, List<EventScheduleWindow>> outletScheduleWindows;
    private final Boolean seasonalAvailabilityChanged;
    private final Long seasonalAvailabilityVersion;
    private final LocalDate seasonalStartDate;
    private final LocalDate seasonalEndDate;
    private final String seasonalTimezone;

    public MenuItemChangedEvent(UUID eventId, UUID originalEventId, CatalogEventType eventType,
            Long itemId, Long restaurantChainId, Long outletId, Long aggregateVersion,
            String itemName, BigDecimal price, Boolean availability, Boolean visibility,
            Instant occurredAt, String correlationId, MenuItemStatus status) {
        this(eventId, originalEventId, eventType, itemId, restaurantChainId, outletId, aggregateVersion,
                itemName, price, availability, visibility, occurredAt, correlationId, status,
                false, null, null, Map.of());
    }

    public MenuItemChangedEvent(UUID eventId, UUID originalEventId, CatalogEventType eventType,
            Long itemId, Long restaurantChainId, Long outletId, Long aggregateVersion,
            String itemName, BigDecimal price, Boolean availability, Boolean visibility,
            Instant occurredAt, String correlationId, MenuItemStatus status,
            Boolean scheduleChanged, Long scheduleVersion) {
        this(eventId, originalEventId, eventType, itemId, restaurantChainId, outletId, aggregateVersion,
                itemName, price, availability, visibility, occurredAt, correlationId, status,
                scheduleChanged, scheduleVersion, null, Map.of());
    }

    public MenuItemChangedEvent(UUID eventId, UUID originalEventId, CatalogEventType eventType,
            Long itemId, Long restaurantChainId, Long outletId, Long aggregateVersion,
            String itemName, BigDecimal price, Boolean availability, Boolean visibility,
            Instant occurredAt, String correlationId, MenuItemStatus status,
            Boolean scheduleChanged, Long scheduleVersion, String scheduleTimezone,
            Map<String, List<EventScheduleWindow>> scheduleWindows) {
        this(eventId, originalEventId, eventType, itemId, restaurantChainId, outletId, aggregateVersion,
                itemName, price, availability, visibility, occurredAt, correlationId, status,
                scheduleChanged, scheduleVersion, scheduleTimezone, scheduleWindows, Map.of(), Map.of());
    }

    public MenuItemChangedEvent(UUID eventId, UUID originalEventId, CatalogEventType eventType,
            Long itemId, Long restaurantChainId, Long outletId, Long aggregateVersion,
            String itemName, BigDecimal price, Boolean availability, Boolean visibility,
            Instant occurredAt, String correlationId, MenuItemStatus status,
            Boolean scheduleChanged, Long scheduleVersion, String scheduleTimezone,
            Map<String, List<EventScheduleWindow>> scheduleWindows,
            Map<String, List<EventScheduleWindow>> baseScheduleWindows,
            Map<String, List<EventScheduleWindow>> outletScheduleWindows) {
        this(eventId, originalEventId, eventType, itemId, restaurantChainId, outletId, aggregateVersion,
                itemName, price, availability, visibility, occurredAt, correlationId, status, scheduleChanged,
                scheduleVersion, scheduleTimezone, scheduleWindows, baseScheduleWindows, outletScheduleWindows,
                false, null, null, null, null);
    }

    public MenuItemChangedEvent(UUID eventId, UUID originalEventId, CatalogEventType eventType,
            Long itemId, Long restaurantChainId, Long outletId, Long aggregateVersion,
            String itemName, BigDecimal price, Boolean availability, Boolean visibility,
            Instant occurredAt, String correlationId, MenuItemStatus status,
            Boolean scheduleChanged, Long scheduleVersion, String scheduleTimezone,
            Map<String, List<EventScheduleWindow>> scheduleWindows,
            Map<String, List<EventScheduleWindow>> baseScheduleWindows,
            Map<String, List<EventScheduleWindow>> outletScheduleWindows,
            Boolean seasonalAvailabilityChanged, Long seasonalAvailabilityVersion,
            LocalDate seasonalStartDate, LocalDate seasonalEndDate, String seasonalTimezone) {

        if (itemName == null && price == null && visibility == null && availability == null
                && status == null && !Boolean.TRUE.equals(scheduleChanged)) {
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
        if (Boolean.TRUE.equals(scheduleChanged) && (scheduleVersion == null || scheduleVersion < 0)) {
            throw new IllegalArgumentException("Schedule version must be non-negative when schedule changed");
        }
        if (Boolean.TRUE.equals(scheduleChanged) && (scheduleTimezone == null || scheduleTimezone.isBlank())) {
            throw new IllegalArgumentException("Schedule timezone is required when schedule changed");
        }
        if (Boolean.TRUE.equals(seasonalAvailabilityChanged)
                && (seasonalAvailabilityVersion == null || seasonalAvailabilityVersion < 0)) {
            throw new IllegalArgumentException("Seasonal availability version must be non-negative when changed");
        }
        if (Boolean.TRUE.equals(seasonalAvailabilityChanged)
                && (seasonalTimezone == null || seasonalTimezone.isBlank())) {
            throw new IllegalArgumentException("Seasonal availability timezone is required when changed");
        }
        if ((seasonalStartDate == null) != (seasonalEndDate == null)
                || (seasonalStartDate != null && !seasonalStartDate.isBefore(seasonalEndDate))) {
            throw new IllegalArgumentException("Seasonal availability dates must be an increasing complete range");
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
        this.scheduleChanged = scheduleChanged;
        this.scheduleVersion = scheduleVersion;
        this.scheduleId = Boolean.TRUE.equals(scheduleChanged)
                ? itemId + ":" + (outletId == null ? "base" : outletId)
                : null;
        this.scheduleTimezone = scheduleTimezone;
        this.scheduleWindows = scheduleWindows == null ? Map.of() : Map.copyOf(scheduleWindows);
        this.baseScheduleWindows = baseScheduleWindows == null ? Map.of() : Map.copyOf(baseScheduleWindows);
        this.outletScheduleWindows = outletScheduleWindows == null ? Map.of() : Map.copyOf(outletScheduleWindows);
        this.seasonalAvailabilityChanged = seasonalAvailabilityChanged;
        this.seasonalAvailabilityVersion = seasonalAvailabilityVersion;
        this.seasonalStartDate = seasonalStartDate;
        this.seasonalEndDate = seasonalEndDate;
        this.seasonalTimezone = seasonalTimezone;
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

    public Long getOutletId() {
        return outletId;
    }

    public Long getAggregateVersion() {
        return aggregateVersion;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getItemName() {
        return itemName;
    }

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

    public Boolean getScheduleChanged() {
        return scheduleChanged;
    }

    public Long getScheduleVersion() {
        return scheduleVersion;
    }

    public String getScheduleId() {
        return scheduleId;
    }

    public String getScheduleTimezone() {
        return scheduleTimezone;
    }

    public Map<String, List<EventScheduleWindow>> getScheduleWindows() {
        return scheduleWindows;
    }

    public Map<String, List<EventScheduleWindow>> getBaseScheduleWindows() {
        return baseScheduleWindows;
    }

    public Map<String, List<EventScheduleWindow>> getOutletScheduleWindows() {
        return outletScheduleWindows;
    }
    public Boolean getSeasonalAvailabilityChanged() { return seasonalAvailabilityChanged; }
    public Long getSeasonalAvailabilityVersion() { return seasonalAvailabilityVersion; }
    public LocalDate getSeasonalStartDate() { return seasonalStartDate; }
    public LocalDate getSeasonalEndDate() { return seasonalEndDate; }
    public String getSeasonalTimezone() { return seasonalTimezone; }
}
