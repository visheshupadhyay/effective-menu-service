package com.vishesh.effectivemenu.snapshot.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vishesh.effectivemenu.snapshot.application.EffectiveMenuSnapshotService;
import com.vishesh.effectivemenu.snapshot.domain.EffectiveMenuSnapshot;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

/** Internal endpoint used by Catalog reconciliation, not by customers. */
@RestController
@RequestMapping("/internal/effective-menu")
public class InternalEffectiveMenuController {
    private final EffectiveMenuSnapshotService service;

    public InternalEffectiveMenuController(EffectiveMenuSnapshotService service) {
        this.service = service;
    }

    @GetMapping("/items/{menuItemId}/outlets/{outletId}")
    public ResponseEntity<EffectiveMenuItemStateResponse> getItemState(
            @PathVariable Long menuItemId, @PathVariable Long outletId) {
        if (menuItemId == null || menuItemId <= 0 || outletId == null || outletId <= 0) {
            throw new IllegalArgumentException("Menu item and outlet IDs must be positive");
        }

        EffectiveMenuSnapshot snapshot = service.getSnapshot(outletId);
        JsonNode items = snapshot.getEffectiveMenu().get("items");
        if (items == null || !items.isArray()) {
            throw new EffectiveMenuItemNotFoundException(menuItemId, outletId);
        }
        for (JsonNode item : (ArrayNode) items) {
            if (item.path("id").asLong(-1L) == menuItemId) {
                return ResponseEntity.ok(new EffectiveMenuItemStateResponse(
                        menuItemId,
                        outletId,
                        item.get("price") == null ? null : item.get("price").decimalValue(),
                        item.path("availability").asBoolean(),
                        item.path("visibility").asBoolean(),
                        snapshot.getSnapshotVersion()));
            }
        }
        throw new EffectiveMenuItemNotFoundException(menuItemId, outletId);
    }
}
