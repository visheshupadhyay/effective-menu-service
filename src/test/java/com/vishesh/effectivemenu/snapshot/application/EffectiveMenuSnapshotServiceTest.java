package com.vishesh.effectivemenu.snapshot.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import com.vishesh.effectivemenu.snapshot.domain.EffectiveMenuSnapshot;
import com.vishesh.effectivemenu.snapshot.cache.EffectiveMenuCache;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class EffectiveMenuSnapshotServiceTest {

    @Mock
    private EffectiveMenuSnapshotRepository repository;

    @Mock
    private EffectiveMenuCache cache;

    private EffectiveMenuSnapshotService service;
    private EffectiveMenuSnapshot snapshot;

    @BeforeEach
    void setUp() throws Exception {
        service = new EffectiveMenuSnapshotService(repository, cache);
        var menu = new ObjectMapper().readTree("{\"items\":[]}");
        var timestamp = Instant.parse("2026-01-01T00:00:00Z");
        snapshot = new EffectiveMenuSnapshot(501L, 7L, menu, timestamp, timestamp);
    }

    @Test
    void getSnapshot_whenFound_returnsSnapshot() {
        when(cache.get(501L)).thenReturn(Optional.empty());
        when(repository.findByOutletId(501L)).thenReturn(Optional.of(snapshot));

        assertSame(snapshot, service.getSnapshot(501L));
        verify(repository).findByOutletId(501L);
        verify(cache).put(501L, snapshot);
    }

    @Test
    void getSnapshot_whenCacheHasSnapshot_doesNotCallRepository() {
        when(cache.get(501L)).thenReturn(Optional.of(snapshot));

        assertSame(snapshot, service.getSnapshot(501L));

        verify(repository, never()).findByOutletId(501L);
    }

    @Test
    void getSnapshot_whenMissing_throwsUnavailableException() {
        when(cache.get(501L)).thenReturn(Optional.empty());
        when(repository.findByOutletId(501L)).thenReturn(Optional.empty());

        var exception = assertThrows(EffectiveMenuSnapshotUnavailableException.class,
                () -> service.getSnapshot(501L));

        assertEquals(501L, exception.getOutletId());
        assertEquals("Effective menu is not available for outlet 501", exception.getMessage());
        verify(repository).findByOutletId(501L);
        verify(cache, never()).put(501L, snapshot);
    }

    @Test
    void invalidOutletIds_areRejectedWithoutRepositoryCall() {
        assertThrows(IllegalArgumentException.class, () -> service.getSnapshot(null));
        assertThrows(IllegalArgumentException.class, () -> service.getSnapshot(0L));
        assertThrows(IllegalArgumentException.class, () -> service.getSnapshot(-1L));

        verifyNoInteractions(repository, cache);
    }

    @Test
    void getCustomerSnapshot_filtersHiddenUnavailableAndArchivedItems() throws Exception {
        var menu = new ObjectMapper().readTree("""
                {"items":[
                  {"id":1,"visibility":true,"availability":true,"status":"ACTIVE"},
                  {"id":2,"visibility":false,"availability":true,"status":"HIDDEN"},
                  {"id":3,"visibility":true,"availability":false,"status":"ACTIVE"},
                  {"id":4,"visibility":true,"availability":true,"status":"ARCHIVED"}
                ]}
                """);
        Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");
        EffectiveMenuSnapshot stored = new EffectiveMenuSnapshot(501L, 8L, menu, timestamp, timestamp);
        when(cache.get(501L)).thenReturn(Optional.empty());
        when(repository.findByOutletId(501L)).thenReturn(Optional.of(stored));

        EffectiveMenuSnapshot customerSnapshot = service.getCustomerSnapshot(501L);

        assertEquals(2, customerSnapshot.getEffectiveMenu().get("items").size());
        assertEquals(1L, customerSnapshot.getEffectiveMenu().get("items").get(0).get("id").asLong());
        assertEquals(3L, customerSnapshot.getEffectiveMenu().get("items").get(1).get("id").asLong());
        assertEquals(4, stored.getEffectiveMenu().get("items").size());
    }

    @Test
    void getCustomerSnapshot_whenCacheHasSnapshot_filtersCachedData() throws Exception {
        var menu = new ObjectMapper().readTree("""
                {"items":[
                  {"id":1,"visibility":true,"availability":true,"status":"ACTIVE"},
                  {"id":2,"visibility":false,"availability":true,"status":"HIDDEN"}
                ]}
                """);
        Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");
        EffectiveMenuSnapshot cached = new EffectiveMenuSnapshot(501L, 9L, menu, timestamp, timestamp);
        when(cache.get(501L)).thenReturn(Optional.of(cached));

        EffectiveMenuSnapshot customerSnapshot = service.getCustomerSnapshot(501L);

        assertEquals(1, customerSnapshot.getEffectiveMenu().get("items").size());
        assertEquals(1L, customerSnapshot.getEffectiveMenu().get("items").get(0).get("id").asLong());
        verify(repository, never()).findByOutletId(501L);
    }
}
