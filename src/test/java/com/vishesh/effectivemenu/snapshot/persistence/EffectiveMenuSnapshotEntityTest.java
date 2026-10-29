package com.vishesh.effectivemenu.snapshot.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class EffectiveMenuSnapshotEntityTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Instant calculatedAt = Instant.parse("2026-01-01T00:00:00Z");
    private final Instant updatedAt = Instant.parse("2026-01-01T00:01:00Z");

    @Test
    void validSnapshot_preservesAllFields() throws Exception {
        var menu = objectMapper.readTree("{\"items\":[{\"id\":101,\"price\":12.00}]}");
        EffectiveMenuSnapshotEntity snapshot = new EffectiveMenuSnapshotEntity(501L, 0L, menu, calculatedAt, updatedAt);

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
                () -> new EffectiveMenuSnapshotEntity(0L, 0L, menu, calculatedAt, updatedAt));
        assertThrows(IllegalArgumentException.class,
                () -> new EffectiveMenuSnapshotEntity(501L, -1L, menu, calculatedAt, updatedAt));
        assertThrows(IllegalArgumentException.class,
                () -> new EffectiveMenuSnapshotEntity(501L, 0L, null, calculatedAt, updatedAt));
        assertThrows(IllegalArgumentException.class,
                () -> new EffectiveMenuSnapshotEntity(501L, 0L, menu, null, updatedAt));
        assertThrows(IllegalArgumentException.class,
                () -> new EffectiveMenuSnapshotEntity(501L, 0L, menu, calculatedAt, null));
    }

    @Test
    void nullVersion_isRejected() {
        var menu = objectMapper.createObjectNode();
        assertThrows(IllegalArgumentException.class,
                () -> new EffectiveMenuSnapshotEntity(501L, null, menu, calculatedAt, updatedAt));
    }
}
