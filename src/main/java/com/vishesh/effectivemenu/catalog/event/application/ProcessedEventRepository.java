package com.vishesh.effectivemenu.catalog.event.application;

import java.time.Instant;
import java.util.UUID;

import com.vishesh.effectivemenu.catalog.event.CatalogEventType;

/** Stores event IDs so duplicate Kafka deliveries can be ignored. */
public interface ProcessedEventRepository {
    boolean exists(UUID eventId);

    void save(UUID eventId, 
        CatalogEventType eventType, 
        Instant processedAt);
}
