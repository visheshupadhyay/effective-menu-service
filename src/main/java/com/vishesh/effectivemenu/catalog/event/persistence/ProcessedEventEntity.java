package com.vishesh.effectivemenu.catalog.event.persistence;

import java.time.Instant;
import java.util.UUID;

import com.vishesh.effectivemenu.catalog.event.CatalogEventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "processed_event")
/** Database row recording one successfully processed event ID. */
public class ProcessedEventEntity {
    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 100)
    private CatalogEventType eventType;

    protected ProcessedEventEntity() {
       
    }

    public ProcessedEventEntity(UUID eventId, CatalogEventType eventType, Instant processedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = processedAt;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public CatalogEventType getEventType() {
        return eventType;
    }
}
