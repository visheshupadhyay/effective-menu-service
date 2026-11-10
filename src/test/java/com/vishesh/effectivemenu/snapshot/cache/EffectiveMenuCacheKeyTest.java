package com.vishesh.effectivemenu.snapshot.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EffectiveMenuCacheKeyTest {
    private final EffectiveMenuCacheKey key = new EffectiveMenuCacheKey("effective-menu:outlet:");

    @Test
    void createsSeparateMenuAndLockKeysForTheSameOutlet() {
        assertEquals("effective-menu:outlet:501", key.createKey(501L));
        assertEquals("effective-menu:lock:outlet:501", key.createLockKey(501L));
    }

    @Test
    void lockAndMenuKeysAreDifferent() {
        assertNotEquals(key.createKey(501L), key.createLockKey(501L));
    }

    @Test
    void lockKeyRejectsInvalidOutletId() {
        assertThrows(IllegalArgumentException.class, () -> key.createLockKey(0L));
    }
}
