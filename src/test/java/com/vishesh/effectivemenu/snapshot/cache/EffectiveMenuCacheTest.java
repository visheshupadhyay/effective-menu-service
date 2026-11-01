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

    private EffectiveMenuCache cache;

    @BeforeEach
    void setUp() {
        cache = new EffectiveMenuCache(
                redisTemplate,
                new EffectiveMenuCacheKey("effective-menu:outlet:"),
                objectMapper,
                true,
                TTL);
    }

    @Test
    void get_whenRedisHasNoValue_returnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get(KEY)).thenReturn(null);

        assertEquals(Optional.empty(), cache.get(OUTLET_ID));
    }

    @Test
    void get_whenRedisHasJson_returnsSnapshot() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get(KEY)).thenReturn("snapshot-json");
        when(objectMapper.readValue("snapshot-json", EffectiveMenuSnapshot.class))
                .thenReturn(snapshot);

        assertEquals(Optional.of(snapshot), cache.get(OUTLET_ID));
    }

    @Test
    void put_serializesSnapshotAndUsesConfiguredTtl() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(objectMapper.writeValueAsString(snapshot)).thenReturn("snapshot-json");

        cache.put(OUTLET_ID, snapshot);

        verify(values).set(KEY, "snapshot-json", TTL);
    }

    @Test
    void evict_deletesOutletKey() {
        cache.evict(OUTLET_ID);

        verify(redisTemplate).delete(KEY);
    }

    @Test
    void put_withNullSnapshot_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> cache.put(OUTLET_ID, null));
    }

    @Test
    void constructor_withNonPositiveTtl_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> new EffectiveMenuCache(
                redisTemplate,
                new EffectiveMenuCacheKey("effective-menu:outlet:"),
                objectMapper,
                true,
                Duration.ZERO));
    }
}
