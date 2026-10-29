package com.vishesh.effectivemenu.snapshot.api;

import java.time.Instant;

import tools.jackson.databind.JsonNode;

/** JSON response returned by the customer menu endpoint. */
public record EffectiveMenuSnapshotResponse(
        Long outletId,
        Long snapshotVersion,
        JsonNode effectiveMenu,
        Instant calculatedAt,
        Instant updatedAt) {
}
