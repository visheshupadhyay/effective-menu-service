package com.vishesh.effectivemenu.snapshot.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.vishesh.effectivemenu.snapshot.application.EffectiveMenuSnapshotService;
import com.vishesh.effectivemenu.snapshot.application.EffectiveMenuSnapshotUnavailableException;
import com.vishesh.effectivemenu.snapshot.domain.EffectiveMenuSnapshot;

import tools.jackson.databind.ObjectMapper;

@WebMvcTest(EffectiveMenuSnapshotController.class)
class EffectiveMenuSnapshotControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EffectiveMenuSnapshotService service;

    @Test
    void getMenu_whenSnapshotExists_returns200WithSnapshotFields() throws Exception {
        var timestamp = Instant.parse("2026-01-01T00:00:00Z");
        var menu = new ObjectMapper().readTree("{\"items\":[{\"id\":101}]} ");
        when(service.getCustomerSnapshot(501L))
                .thenReturn(new EffectiveMenuSnapshot(501L, 7L, menu, timestamp, timestamp));

        mockMvc.perform(get("/api/v1/outlets/501/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outletId").value(501))
                .andExpect(jsonPath("$.snapshotVersion").value(7))
                .andExpect(jsonPath("$.effectiveMenu.items[0].id").value(101))
                .andExpect(jsonPath("$.calculatedAt").value("2026-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-01-01T00:00:00Z"));

        verify(service).getCustomerSnapshot(501L);
    }

    @Test
    void getMenu_whenSnapshotMissing_returns503ApiError() throws Exception {
        when(service.getCustomerSnapshot(501L))
                .thenThrow(new EffectiveMenuSnapshotUnavailableException(501L));

        mockMvc.perform(get("/api/v1/outlets/501/menu"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message")
                        .value("Effective menu is not available for outlet 501"))
                .andExpect(jsonPath("$.path").value("/api/v1/outlets/501/menu"));
    }

    @Test
    void getMenu_whenOutletIdIsInvalid_returns400ApiError() throws Exception {
        when(service.getCustomerSnapshot(0L))
                .thenThrow(new IllegalArgumentException("Outlet ID must be positive"));

        mockMvc.perform(get("/api/v1/outlets/0/menu"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Outlet ID must be positive"))
                .andExpect(jsonPath("$.path").value("/api/v1/outlets/0/menu"));
    }
}
