package com.vishesh.effectivemenu.snapshot.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import com.vishesh.effectivemenu.snapshot.domain.EffectiveMenuSnapshot;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class EffectiveMenuSnapshotPersistenceAdapterTest {

    @Mock
    private EffectiveMenuSnapshotJpaRepository repository;

    private EffectiveMenuSnapshotPersistenceAdapter adapter;
    private ObjectMapper objectMapper;
    private Instant calculatedAt;
    private Instant updatedAt;

    @BeforeEach
    void setUp() {
        adapter = new EffectiveMenuSnapshotPersistenceAdapter(repository);
        objectMapper = new ObjectMapper();
        calculatedAt = Instant.parse("2026-01-01T00:00:00Z");
        updatedAt = Instant.parse("2026-01-01T00:01:00Z");
    }

    @Test
    void save_mapsDomainToEntity_andReturnsPersistedDomain() throws Exception {
        var menu = objectMapper.readTree("{\"items\":[{\"id\":101}]} ");
        var snapshot = new EffectiveMenuSnapshot(501L, 7L, menu, calculatedAt, updatedAt);
        var persisted = new EffectiveMenuSnapshotEntity(501L, 8L, menu, calculatedAt, updatedAt);
        when(repository.save(any(EffectiveMenuSnapshotEntity.class))).thenReturn(persisted);

        var result = adapter.save(snapshot);

        var savedEntity = ArgumentCaptor.forClass(EffectiveMenuSnapshotEntity.class);
        verify(repository).save(savedEntity.capture());
        assertEquals(501L, savedEntity.getValue().getOutletId());
        assertEquals(7L, savedEntity.getValue().getSnapshotVersion());
        assertEquals(calculatedAt, savedEntity.getValue().getCalculatedAt());
        assertEquals(updatedAt, savedEntity.getValue().getUpdatedAt());
        assertEquals(501L, result.getOutletId());
        assertEquals(8L, result.getSnapshotVersion());
        assertEquals(menu, result.getEffectiveMenu());
    }

    @Test
    void findByOutletId_mapsFoundEntityToDomain() throws Exception {
        var menu = objectMapper.readTree("{\"items\":[]}");
        var entity = new EffectiveMenuSnapshotEntity(501L, 7L, menu, calculatedAt, updatedAt);
        when(repository.findById(501L)).thenReturn(Optional.of(entity));

        var result = adapter.findByOutletId(501L);

        var returned = result.orElseThrow();
        assertEquals(501L, returned.getOutletId());
        assertEquals(7L, returned.getSnapshotVersion());
        assertEquals(menu, returned.getEffectiveMenu());
        assertEquals(calculatedAt, returned.getCalculatedAt());
        assertEquals(updatedAt, returned.getUpdatedAt());
        verify(repository).findById(501L);
    }

    @Test
    void findByOutletId_whenMissing_returnsEmpty() {
        when(repository.findById(501L)).thenReturn(Optional.empty());

        assertEquals(Optional.empty(), adapter.findByOutletId(501L));
        verify(repository).findById(501L);
    }

    @Test
    void invalidArguments_areRejected() {
        assertThrows(IllegalArgumentException.class, () -> adapter.save(null));
        assertThrows(IllegalArgumentException.class, () -> adapter.findByOutletId(null));
        assertThrows(IllegalArgumentException.class, () -> adapter.findByOutletId(0L));
        assertThrows(IllegalArgumentException.class, () -> adapter.findByOutletId(-1L));
        verifyNoInteractions(repository);
    }
}
