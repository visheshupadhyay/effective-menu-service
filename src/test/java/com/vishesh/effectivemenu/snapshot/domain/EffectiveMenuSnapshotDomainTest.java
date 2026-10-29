package com.vishesh.effectivemenu.snapshot.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class EffectiveMenuSnapshotTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Instant calculatedAt = Instant.parse("2026-01-01T00:00:00Z");
    private final Instant updatedAt = Instant.parse("2026-01-01T00:01:00Z");

    @Test
    void validSnapshot_preservesAllValues() throws Exception {
        var menu = objectMapper.readTree("{\"items\":[{\"id\":101}]} ");

        var snapshot = new EffectiveMenuSnapshot(501L, 0L, menu, calculatedAt, updatedAt);

        assertEquals(501L, snapshot.getOutletId());
        assertEquals(0L, snapshot.getSnapshotVersion());
        assertEquals(menu, snapshot.getEffectiveMenu());
        assertEquals(calculatedAt, snapshot.getCalculatedAt());
        assertEquals(updatedAt, snapshot.getUpdatedAt());
    }

    @Test
    void invalidValues_areRejected() {
        var menu = objectMapper.createObjectNode();

        assertThrows(IllegalArgumentException.class,
                () -> new EffectiveMenuSnapshot(null, 0L, menu, calculatedAt, updatedAt));
        assertThrows(IllegalArgumentException.class,
                () -> new EffectiveMenuSnapshot(0L, 0L, menu, calculatedAt, updatedAt));
        assertThrows(IllegalArgumentException.class,
                () -> new EffectiveMenuSnapshot(501L, -1L, menu, calculatedAt, updatedAt));
        assertThrows(IllegalArgumentException.class,
                () -> new EffectiveMenuSnapshot(501L, null, menu, calculatedAt, updatedAt));
        assertThrows(IllegalArgumentException.class,
                () -> new EffectiveMenuSnapshot(501L, 0L, null, calculatedAt, updatedAt));
        assertThrows(IllegalArgumentException.class,
                () -> new EffectiveMenuSnapshot(501L, 0L, menu, null, updatedAt));
        assertThrows(IllegalArgumentException.class,
                () -> new EffectiveMenuSnapshot(501L, 0L, menu, calculatedAt, null));
    }
}
