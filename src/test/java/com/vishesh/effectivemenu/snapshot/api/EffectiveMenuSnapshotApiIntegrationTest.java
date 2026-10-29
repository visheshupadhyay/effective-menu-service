package com.vishesh.effectivemenu.snapshot.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.vishesh.effectivemenu.TestcontainersConfiguration;
import com.vishesh.effectivemenu.snapshot.persistence.EffectiveMenuSnapshotEntity;
import com.vishesh.effectivemenu.snapshot.persistence.EffectiveMenuSnapshotJpaRepository;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EffectiveMenuSnapshotApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EffectiveMenuSnapshotJpaRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void getMenu_whenSnapshotExists_returnsPersistedSnapshot() throws Exception {
        Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");
        var menu = objectMapper.readTree(
                "{\"items\":[{\"id\":101,\"name\":\"Burger\",\"price\":12.00}]}" );

        repository.save(new EffectiveMenuSnapshotEntity(501L, 7L, menu, timestamp, timestamp));

        mockMvc.perform(get("/api/v1/outlets/501/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outletId").value(501))
                .andExpect(jsonPath("$.snapshotVersion").value(7))
                .andExpect(jsonPath("$.effectiveMenu.items[0].id").value(101))
                .andExpect(jsonPath("$.effectiveMenu.items[0].price").value(12.0));
    }

    @Test
    void getMenu_whenSnapshotDoesNotExist_returns503() throws Exception {
        mockMvc.perform(get("/api/v1/outlets/999/menu"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message")
                        .value("Effective menu is not available for outlet 999"))
                .andExpect(jsonPath("$.path").value("/api/v1/outlets/999/menu"));
    }
}
