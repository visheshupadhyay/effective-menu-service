package com.vishesh.effectivemenu.catalog.event.infrastructure;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;

import com.vishesh.effectivemenu.catalog.event.MenuItemChangedEvent;
import com.vishesh.effectivemenu.catalog.event.application.MenuItemChangedEventHandler;
import com.vishesh.effectivemenu.monitoring.EffectiveMenuOperationalMetrics;

import tools.jackson.databind.ObjectMapper;

/** Listens for Catalog events and hands validated messages to the handler. */
@Component
/** Reads catalog events from Kafka, validates them, and delegates business handling. */
public class MenuItemChangedKafkaConsumer {
    private final ObjectMapper objectMapper;
    private final MenuItemChangedEventHandler handler;
    private final EffectiveMenuOperationalMetrics metrics;

    public MenuItemChangedKafkaConsumer(
            ObjectMapper objectMapper,
            @Qualifier("defaultMenuItemChangedEventHandler") MenuItemChangedEventHandler handler,
            EffectiveMenuOperationalMetrics metrics) {
        this.objectMapper = objectMapper;
        this.handler = handler;
        this.metrics = metrics;
    }

    @KafkaListener(topics = "${app.kafka.menu-item-changed-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String payload) {
        try {
            if (payload == null || payload.isBlank()) {
                throw new IllegalArgumentException("Kafka payload cannot be null or blank");
            }

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
                    dto.getStatus());

            handler.handle(event);
        } catch (Exception e) {
            metrics.recordFailure();
            throw e;
        }

    }
}
