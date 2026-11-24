package com.vishesh.effectivemenu.catalog.event.infrastructure;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.vishesh.effectivemenu.catalog.event.MenuItemChangedEvent;
import com.vishesh.effectivemenu.catalog.event.CatalogEventType;
import com.vishesh.effectivemenu.catalog.event.OutletMenuInitializedEvent;
import com.vishesh.effectivemenu.catalog.event.application.MenuItemChangedEventHandler;
import com.vishesh.effectivemenu.catalog.event.application.OutletMenuInitializedEventHandler;
import com.vishesh.effectivemenu.monitoring.EffectiveMenuOperationalMetrics;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Listens for Catalog events and hands validated messages to the handler. */
@Component
/**
 * Reads catalog events from Kafka, validates them, and delegates business
 * handling.
 */
public class MenuItemChangedKafkaConsumer {
    private final ObjectMapper objectMapper;
    private final MenuItemChangedEventHandler menuItemChangedHandler;
    private final OutletMenuInitializedEventHandler outletInitializedHandler;
    private final EffectiveMenuOperationalMetrics metrics;

    @Autowired
    public MenuItemChangedKafkaConsumer(
            ObjectMapper objectMapper,
            @Qualifier("defaultMenuItemChangedEventHandler") MenuItemChangedEventHandler menuItemChangedHandler,
            @Qualifier("defaultOutletMenuInitializedEventHandler") OutletMenuInitializedEventHandler outletInitializedHandler,
            EffectiveMenuOperationalMetrics metrics) {
        this.objectMapper = objectMapper;
        this.menuItemChangedHandler = menuItemChangedHandler;
        this.outletInitializedHandler = outletInitializedHandler;
        this.metrics = metrics;
    }

    /** Kept for existing focused tests of the normal menu-item event path. */
    MenuItemChangedKafkaConsumer(
            ObjectMapper objectMapper,
            MenuItemChangedEventHandler menuItemChangedHandler,
            EffectiveMenuOperationalMetrics metrics) {
        this(objectMapper, menuItemChangedHandler,
                event -> {
                    throw new IllegalStateException("Outlet initialization handler is required");
                },
                metrics);
    }

    @KafkaListener(topics = "${app.kafka.menu-item-changed-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String payload) {
        try {
            if (payload == null || payload.isBlank()) {
                throw new IllegalArgumentException("Kafka payload cannot be null or blank");
            }

            JsonNode envelope = objectMapper.readTree(payload);
            CatalogEventType eventType = readEventType(envelope);
            if (eventType == CatalogEventType.OUTLET_MENU_INITIALIZED) {
                handleOutletInitialization(payload);
            } else {
                handleMenuItemChange(payload);
            }
        } catch (Exception e) {
            metrics.recordFailure();
            throw e;
        }

    }

    private CatalogEventType readEventType(JsonNode envelope) {
        if (envelope == null || !envelope.isObject()) {
            throw new IllegalArgumentException("Kafka payload must be a JSON object");
        }
        String eventTypeValue = envelope.path("eventType").asString(null);
        if (eventTypeValue == null || eventTypeValue.isBlank()) {
            throw new IllegalArgumentException("Kafka event type is required");
        }
        return CatalogEventType.valueOf(eventTypeValue);
    }

    private void handleOutletInitialization(String payload) {
        OutletMenuInitializedEventPayload dto = objectMapper.readValue(payload, OutletMenuInitializedEventPayload.class);
        OutletMenuInitializedEvent event = new OutletMenuInitializedEvent(
                dto.getEventId(),
                dto.getOutletId(),
                dto.getRestaurantChainId(),
                dto.getMenuGroupId(),
                dto.getOutletVersion(),
                dto.getOutletTimezone(),
                dto.getOccurredAt(),
                dto.getCorrelationId(),
                dto.getMenuItems());
        outletInitializedHandler.handle(event);
    }

    private void handleMenuItemChange(String payload) {
        MenuItemChangedEventPayload dto = objectMapper.readValue(payload, MenuItemChangedEventPayload.class);

        if (dto.getStatus() == null) {
            throw new IllegalArgumentException("Kafka menu-item event status cannot be null");
        }

        MenuItemChangedEvent event = new MenuItemChangedEvent(
                dto.getEventId(),
                dto.getOriginalEventId(),
                dto.getEventType(),
                dto.getItemId(),
                dto.getRestaurantChainId(),
                dto.getOutletId(),
                dto.getAggregateVersion(),
                dto.getItemName(),
                dto.getPrice(),
                dto.getAvailability(),
                dto.getVisibility(),
                dto.getOccurredAt(),
                dto.getCorrelationId(),
                dto.getStatus(),
                dto.getScheduleChanged(),
                dto.getScheduleVersion(), dto.getScheduleTimezone(), dto.getScheduleWindows(),
                dto.getBaseScheduleWindows(), dto.getOutletScheduleWindows(),
                dto.getSeasonalAvailabilityChanged(), dto.getSeasonalAvailabilityVersion(),
                dto.getSeasonalStartDate(), dto.getSeasonalEndDate(), dto.getSeasonalTimezone(),
                dto.getModifierConfigurationChanged(), dto.getModifierGroups(),
                dto.getBundleConfigurationChanged(), dto.getBundleSnapshot());

        menuItemChangedHandler.handle(event);
    }
}
