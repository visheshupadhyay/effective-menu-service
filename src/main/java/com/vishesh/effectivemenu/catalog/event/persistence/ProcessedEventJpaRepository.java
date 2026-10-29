package com.vishesh.effectivemenu.catalog.event.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for processed-event rows. */
public interface ProcessedEventJpaRepository extends  JpaRepository<ProcessedEventEntity,UUID>{
    boolean existsById(UUID eventId);
}
