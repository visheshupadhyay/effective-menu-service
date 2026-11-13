package com.vishesh.effectivemenu.snapshot.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;

import java.time.Instant;
import java.time.Duration;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import com.vishesh.effectivemenu.snapshot.domain.EffectiveMenuSnapshot;
import com.vishesh.effectivemenu.snapshot.cache.EffectiveMenuCache;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

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
    void getSnapshot_whenRedisIsUnavailable_fallsBackToPostgres() {
        // EffectiveMenuCache converts a Redis connection failure into a cache miss.
        // The service must then read the source-of-truth snapshot from PostgreSQL.
        when(cache.get(501L)).thenReturn(Optional.empty());
        when(repository.findByOutletId(501L)).thenReturn(Optional.of(snapshot));

        EffectiveMenuSnapshot result = service.getSnapshot(501L);

        assertSame(snapshot, result);
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

    @Test
    void getCustomerSnapshot_recalculatesScheduleAtReadTimeEvenFromCache() throws Exception {
        service = new EffectiveMenuSnapshotService(repository, cache, false,
                Duration.ofSeconds(5), Duration.ZERO, Duration.ZERO,
                new com.vishesh.effectivemenu.snapshot.schedule.WeeklyScheduleEvaluator(),
                Clock.fixed(Instant.parse("2026-01-05T12:00:00Z"), ZoneOffset.UTC));
        var menu = new ObjectMapper().readTree("""
                {"items":[{
                  "id":1,"visibility":true,"availability":true,"status":"ACTIVE",
                  "_schedule":{"timezone":"UTC","catalogAvailability":true,"windows":{
                    "MONDAY":[{"startTime":"09:00","endTime":"11:00"}]
                  }}
                }]}""");
        EffectiveMenuSnapshot cached = new EffectiveMenuSnapshot(501L, 9L, menu,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
        when(cache.get(501L)).thenReturn(Optional.of(cached));

        EffectiveMenuSnapshot customerSnapshot = service.getCustomerSnapshot(501L);

        JsonNode item = customerSnapshot.getEffectiveMenu().get("items").get(0);
        assertEquals(false, item.get("availability").asBoolean());
        assertTrue(item.get("_schedule") == null);
        verify(repository, never()).findByOutletId(501L);
    }

    @Test
    void getSnapshot_whenLockIsAcquired_rechecksCacheAndReleasesLock() {
        service = new EffectiveMenuSnapshotService(repository, cache, true,
                Duration.ofSeconds(5), Duration.ZERO, Duration.ZERO);
        when(cache.get(501L)).thenReturn(Optional.empty(), Optional.of(snapshot));
        when(cache.tryAcquireLock(
                org.mockito.ArgumentMatchers.eq(501L),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(5))))
                .thenReturn(true);

        assertSame(snapshot, service.getSnapshot(501L));

        verify(repository, never()).findByOutletId(501L);
        verify(cache).releaseLock(org.mockito.ArgumentMatchers.eq(501L),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void getSnapshot_whenLockIsUnavailableAndCacheAppears_returnsCachedSnapshot() {
        service = new EffectiveMenuSnapshotService(repository, cache, true,
                Duration.ofSeconds(5), Duration.ZERO, Duration.ZERO);
        when(cache.get(501L)).thenReturn(Optional.empty(), Optional.of(snapshot));
        when(cache.tryAcquireLock(
                org.mockito.ArgumentMatchers.eq(501L),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(5))))
                .thenReturn(false);

        assertSame(snapshot, service.getSnapshot(501L));

        verify(repository, never()).findByOutletId(501L);
        verify(cache, never()).releaseLock(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void concurrentCacheMisses_onlyLockOwnerReadsRepository() throws Exception {
        service = new EffectiveMenuSnapshotService(repository, cache, true,
                Duration.ofSeconds(5), Duration.ofSeconds(2), Duration.ofMillis(10));

        AtomicBoolean cached = new AtomicBoolean(false);
        CountDownLatch repositoryStarted = new CountDownLatch(1);
        CountDownLatch allowRepository = new CountDownLatch(1);

        // Both requests initially miss Redis. Once the lock owner writes the
        // snapshot, the waiting request reuses it instead of querying Postgres.
        when(cache.get(501L)).thenAnswer(invocation ->
                cached.get() ? Optional.of(snapshot) : Optional.empty());
        when(cache.tryAcquireLock(eq(501L), anyString(), eq(Duration.ofSeconds(5))))
                .thenReturn(true, false);
        when(repository.findByOutletId(501L)).thenAnswer(invocation -> {
            repositoryStarted.countDown();
            assertTrue(allowRepository.await(2, TimeUnit.SECONDS));
            return Optional.of(snapshot);
        });
        doAnswer(invocation -> {
            cached.set(true);
            return null;
        }).when(cache).put(eq(501L), same(snapshot));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<EffectiveMenuSnapshot> owner = executor.submit(() -> service.getSnapshot(501L));
            assertTrue(repositoryStarted.await(1, TimeUnit.SECONDS));

            Future<EffectiveMenuSnapshot> waiter = executor.submit(() -> service.getSnapshot(501L));
            allowRepository.countDown();

            assertSame(snapshot, owner.get(2, TimeUnit.SECONDS));
            assertSame(snapshot, waiter.get(2, TimeUnit.SECONDS));
            verify(repository).findByOutletId(501L);
            verify(cache).put(501L, snapshot);
        } finally {
            executor.shutdownNow();
        }
    }
}
