package com.vishesh.effectivemenu.catalog.event.infrastructure;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.vishesh.effectivemenu.catalog.event.MenuItemChangedEvent;
import com.vishesh.effectivemenu.catalog.event.application.MenuItemChangedEventHandler;

import tools.jackson.databind.ObjectMapper;

/** Listens for Catalog events and hands validated messages to the handler. */
@Component
public class MenuItemChangedKafkaConsumer {
    private final ObjectMapper objectMapper;
    private final MenuItemChangedEventHandler handler;

    public MenuItemChangedKafkaConsumer(
            ObjectMapper objectMapper,
            MenuItemChangedEventHandler handler) {
        this.objectMapper = objectMapper;
        this.handler = handler;
    }

    @KafkaListener(topics = "${app.kafka.menu-item-changed-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String payload) {
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
    }
}
