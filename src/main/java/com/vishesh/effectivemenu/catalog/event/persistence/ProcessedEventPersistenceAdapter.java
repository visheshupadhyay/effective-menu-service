package com.vishesh.effectivemenu.catalog.event.persistence;

import java.time.Instant;
import java.util.UUID;

import com.vishesh.effectivemenu.catalog.event.CatalogEventType;
import com.vishesh.effectivemenu.catalog.event.application.ProcessedEventRepository;
import org.springframework.stereotype.Repository;

@Repository
/** Converts processed-event domain calls into database operations. */
public class ProcessedEventPersistenceAdapter implements ProcessedEventRepository {

    private final ProcessedEventJpaRepository repository;

    public ProcessedEventPersistenceAdapter(ProcessedEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean exists(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("Event ID cannot be null");
        }
        return repository.existsById(eventId);
    }

    @Override
    public void save(UUID eventId, CatalogEventType eventType, Instant processedAt) {
        if (eventId == null) {
            throw new IllegalArgumentException("Event ID cannot be null");
        }
        if (eventType == null) {
            throw new IllegalArgumentException("Event type cannot be null");
        }
        if (processedAt == null) {
            throw new IllegalArgumentException("Processed timestamp cannot be null");
        }

        ProcessedEventEntity entity = new ProcessedEventEntity(eventId, eventType, processedAt);
        repository.save(entity);
    }
}
