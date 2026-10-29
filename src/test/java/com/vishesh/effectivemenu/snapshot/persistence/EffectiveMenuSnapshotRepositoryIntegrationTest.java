package com.vishesh.effectivemenu.snapshot.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.vishesh.effectivemenu.TestcontainersConfiguration;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EffectiveMenuSnapshotRepositoryIntegrationTest {

    @Autowired
    private EffectiveMenuSnapshotJpaRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void saveAndReload_preservesJsonbMenu() throws Exception {
        var menu = objectMapper.readTree("{\"items\":[{\"id\":101,\"name\":\"Burger\",\"price\":12.00}]}");
        Instant calculatedAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant updatedAt = Instant.parse("2026-01-01T00:01:00Z");

        repository.save(new EffectiveMenuSnapshotEntity(501L, 7L, menu, calculatedAt, updatedAt));

        Optional<EffectiveMenuSnapshotEntity> reloaded = repository.findById(501L);

        assertTrue(reloaded.isPresent());
        assertEquals(501L, reloaded.get().getOutletId());
        assertEquals(7L, reloaded.get().getSnapshotVersion());
        assertEquals(menu, reloaded.get().getEffectiveMenu());
        assertEquals(calculatedAt, reloaded.get().getCalculatedAt());
        assertEquals(updatedAt, reloaded.get().getUpdatedAt());
    }
}
