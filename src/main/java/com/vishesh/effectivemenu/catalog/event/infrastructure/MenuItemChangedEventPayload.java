package com.vishesh.effectivemenu.catalog.event.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Map;

import com.vishesh.effectivemenu.catalog.event.CatalogEventType;
import com.vishesh.effectivemenu.catalog.event.EventScheduleWindow;
import com.vishesh.effectivemenu.catalog.event.MenuItemStatus;

/** Temporary JSON-shaped object used to read a Kafka message safely. */
/** JSON-shaped Kafka input before it becomes a validated domain event. */
public class MenuItemChangedEventPayload {
    private UUID eventId;
    private UUID originalEventId;
    private CatalogEventType eventType;
    private Long itemId;
    private Long restaurantChainId;
    private Long outletId;
    private Long aggregateVersion;
    private Instant occurredAt;
    private String itemName;
    private BigDecimal price;
    private Boolean availability;
    private Boolean visibility;
    private String correlationId;
    private MenuItemStatus status;
    private Boolean scheduleChanged;
    private Long scheduleVersion;
    private String scheduleId;
    private String scheduleTimezone;
    private Map<String, List<EventScheduleWindow>> scheduleWindows;
    private Map<String, List<EventScheduleWindow>> baseScheduleWindows;
    private Map<String, List<EventScheduleWindow>> outletScheduleWindows;

    public MenuItemChangedEventPayload() {

    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public void setStatus(MenuItemStatus status) {
        this.status = status;
    }

    public void setOriginalEventId(UUID originalEventId) {
        this.originalEventId = originalEventId;
    }

    public void setEventType(CatalogEventType eventType) {
        this.eventType = eventType;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public void setRestaurantChainId(Long restaurantChainId) {
        this.restaurantChainId = restaurantChainId;
    }

    public void setOutletId(Long outletId) {
        this.outletId = outletId;
    }

    public void setAggregateVersion(Long aggregateVersion) {
        this.aggregateVersion = aggregateVersion;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public void setAvailability(Boolean availability) {
        this.availability = availability;
    }

    public void setVisibility(Boolean visibility) {
        this.visibility = visibility;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public void setScheduleChanged(Boolean scheduleChanged) {
        this.scheduleChanged = scheduleChanged;
    }

    public void setScheduleVersion(Long scheduleVersion) {
        this.scheduleVersion = scheduleVersion;
    }

    public void setScheduleId(String scheduleId) {
        this.scheduleId = scheduleId;
    }

    public void setScheduleTimezone(String scheduleTimezone) {
        this.scheduleTimezone = scheduleTimezone;
    }

    public void setScheduleWindows(Map<String, List<EventScheduleWindow>> scheduleWindows) {
        this.scheduleWindows = scheduleWindows;
    }

    public void setBaseScheduleWindows(Map<String, List<EventScheduleWindow>> value) {
        this.baseScheduleWindows = value;
    }

    public void setOutletScheduleWindows(Map<String, List<EventScheduleWindow>> value) {
        this.outletScheduleWindows = value;
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

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getItemName() {
        return itemName;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Boolean getAvailability() {
        return availability;
    }

    public Boolean getVisibility() {
        return visibility;
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

}
