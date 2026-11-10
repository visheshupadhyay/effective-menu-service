package com.vishesh.effectivemenu.snapshot.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.vishesh.effectivemenu.snapshot.domain.EffectiveMenuSnapshot;
import com.vishesh.effectivemenu.monitoring.EffectiveMenuOperationalMetrics;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class EffectiveMenuCacheTest {
    private static final Long OUTLET_ID = 7L;
    private static final String KEY = "effective-menu:outlet:7";
    private static final Duration TTL = Duration.ofSeconds(60);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> values;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private EffectiveMenuSnapshot snapshot;

    @Mock
    private EffectiveMenuOperationalMetrics metrics;

    private EffectiveMenuCache cache;

    @BeforeEach
    void setUp() {
        cache = new EffectiveMenuCache(
                redisTemplate,
                new EffectiveMenuCacheKey("effective-menu:outlet:"),
                objectMapper,
                true,
                TTL,
                metrics);
    }

    @Test
    void get_whenRedisHasNoValue_returnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get(KEY)).thenReturn(null);

        assertEquals(Optional.empty(), cache.get(OUTLET_ID));
        verify(metrics).recordCacheMiss();
    }

    @Test
    void get_whenRedisHasJson_returnsSnapshot() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get(KEY)).thenReturn("snapshot-json");
        when(objectMapper.readValue("snapshot-json", EffectiveMenuSnapshot.class))
                .thenReturn(snapshot);

        assertEquals(Optional.of(snapshot), cache.get(OUTLET_ID));
        verify(metrics).recordCacheHit();
    }

    @Test
    void get_whenRedisContainsCorruptJson_returnsEmptyAndRecordsReadFailure() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get(KEY)).thenReturn("{not-valid-json}");
        when(objectMapper.readValue("{not-valid-json}", EffectiveMenuSnapshot.class))
                .thenThrow(new IllegalArgumentException("Invalid cached JSON"));

        assertEquals(Optional.empty(), cache.get(OUTLET_ID));
        verify(metrics).recordCacheReadFailure();
        verify(metrics).recordCacheMiss();
        verify(redisTemplate).delete(KEY);
        verify(metrics).recordCacheEviction();
    }

    @Test
    void get_whenRedisFails_returnsEmpty() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis is down"));

        assertEquals(Optional.empty(), cache.get(OUTLET_ID));
        verify(metrics).recordCacheReadFailure();
        verify(metrics).recordCacheMiss();
    }

    @Test
    void put_serializesSnapshotAndUsesConfiguredTtl() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(objectMapper.writeValueAsString(snapshot)).thenReturn("snapshot-json");

        cache.put(OUTLET_ID, snapshot);

        verify(values).set(KEY, "snapshot-json", TTL);
        verify(metrics).recordCacheWrite();
    }

    @Test
    void evict_deletesOutletKey() {
        cache.evict(OUTLET_ID);

        verify(redisTemplate).delete(KEY);
        verify(metrics).recordCacheEviction();
    }

    @Test
    void put_withNullSnapshot_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> cache.put(OUTLET_ID, null));
    }

    @Test
    void put_whenRedisFails_doesNotThrow() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis is down"));

        cache.put(OUTLET_ID, snapshot);
        verify(metrics).recordCacheWriteFailure();
    }

    @Test
    void evict_whenRedisFails_doesNotThrow() {
        when(redisTemplate.delete(KEY)).thenThrow(new RuntimeException("Redis is down"));

        cache.evict(OUTLET_ID);
        verify(metrics).recordCacheEvictionFailure();
    }

    @Test
    void constructor_withNonPositiveTtl_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> new EffectiveMenuCache(
                redisTemplate,
                new EffectiveMenuCacheKey("effective-menu:outlet:"),
                objectMapper,
                true,
                Duration.ZERO,
                metrics));
    }

    @Test
    void tryAcquireLock_whenRedisClaimsKey_returnsTrue() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.setIfAbsent("effective-menu:lock:outlet:7", "token-1", TTL))
                .thenReturn(true);

        assertEquals(true, cache.tryAcquireLock(OUTLET_ID, "token-1", TTL));
        verify(values).setIfAbsent("effective-menu:lock:outlet:7", "token-1", TTL);
    }

    @Test
    void tryAcquireLock_whenAnotherOwnerHasLock_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.setIfAbsent("effective-menu:lock:outlet:7", "token-2", TTL))
                .thenReturn(false);

        assertEquals(false, cache.tryAcquireLock(OUTLET_ID, "token-2", TTL));
    }

    @Test
    void tryAcquireLock_withInvalidArguments_isRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> cache.tryAcquireLock(OUTLET_ID, "", TTL));
        assertThrows(IllegalArgumentException.class,
                () -> cache.tryAcquireLock(OUTLET_ID, "token", Duration.ZERO));
    }

    @Test
    void releaseLock_withInvalidArguments_isRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> cache.releaseLock(OUTLET_ID, ""));
    }

    @Test
    void releaseLock_whenRedisDeletesOwnedLock_returnsTrue() {
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(java.util.Collections.singletonList(
                        "effective-menu:lock:outlet:7")),
                org.mockito.ArgumentMatchers.eq("token-1")))
                .thenReturn(1L);

        assertEquals(true, cache.releaseLock(OUTLET_ID, "token-1"));
    }

    @Test
    void releaseLock_whenTokenDoesNotOwnLock_returnsFalse() {
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(java.util.Collections.singletonList(
                        "effective-menu:lock:outlet:7")),
                org.mockito.ArgumentMatchers.eq("wrong-token")))
                .thenReturn(0L);

        assertEquals(false, cache.releaseLock(OUTLET_ID, "wrong-token"));
    }
}
