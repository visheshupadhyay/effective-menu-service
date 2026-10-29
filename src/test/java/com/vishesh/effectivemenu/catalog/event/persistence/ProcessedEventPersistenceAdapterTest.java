package com.vishesh.effectivemenu.catalog.event.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import com.vishesh.effectivemenu.catalog.event.CatalogEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessedEventPersistenceAdapterTest {

    @Mock
    private ProcessedEventJpaRepository repository;

    private ProcessedEventPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ProcessedEventPersistenceAdapter(repository);
    }

    @Test
    void exists_whenRepositoryDoesNotContainEvent_returnsFalse() {
        UUID eventId = UUID.randomUUID();
        when(repository.existsById(eventId)).thenReturn(false);

        assertFalse(adapter.exists(eventId));
        verify(repository).existsById(eventId);
    }

    @Test
    void exists_whenRepositoryContainsEvent_returnsTrue() {
        UUID eventId = UUID.randomUUID();
        when(repository.existsById(eventId)).thenReturn(true);

        assertTrue(adapter.exists(eventId));
        verify(repository).existsById(eventId);
    }

    @Test
    void exists_whenEventIdIsNull_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> adapter.exists(null));
        verify(repository, never()).existsById(any());
    }

    @Test
    void save_mapsValuesAndPersistsEntity() {
        UUID eventId = UUID.randomUUID();
        Instant processedAt = Instant.parse("2026-01-01T00:00:00Z");

        adapter.save(eventId, CatalogEventType.MENU_ITEM_CHANGED, processedAt);

        ArgumentCaptor<ProcessedEventEntity> captor = ArgumentCaptor.forClass(ProcessedEventEntity.class);
        verify(repository).save(captor.capture());
        ProcessedEventEntity saved = captor.getValue();
        assertEquals(eventId, saved.getEventId());
        assertEquals(CatalogEventType.MENU_ITEM_CHANGED, saved.getEventType());
        assertEquals(processedAt, saved.getProcessedAt());
    }

    @Test
    void save_whenEventIdIsNull_rejectsRequest() {
        assertThrows(IllegalArgumentException.class,
                () -> adapter.save(null, CatalogEventType.MENU_ITEM_CHANGED, Instant.now()));
        verify(repository, never()).save(any(ProcessedEventEntity.class));
    }

    @Test
    void save_whenEventTypeIsNull_rejectsRequest() {
        assertThrows(IllegalArgumentException.class,
                () -> adapter.save(UUID.randomUUID(), null, Instant.now()));
        verify(repository, never()).save(any(ProcessedEventEntity.class));
    }

    @Test
    void save_whenProcessedAtIsNull_rejectsRequest() {
        assertThrows(IllegalArgumentException.class,
                () -> adapter.save(UUID.randomUUID(), CatalogEventType.MENU_ITEM_CHANGED, null));
        verify(repository, never()).save(any(ProcessedEventEntity.class));
    }
}
