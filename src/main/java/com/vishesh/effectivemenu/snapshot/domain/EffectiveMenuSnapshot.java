package com.vishesh.effectivemenu.snapshot.domain;

import java.time.Instant;

import tools.jackson.databind.JsonNode;

/** The latest customer-ready menu copy for one outlet. */
public final class EffectiveMenuSnapshot {

    private final Long outletId;

    private final Long snapshotVersion;

    private final JsonNode effectiveMenu;

    private final Instant calculatedAt;

    private final Instant updatedAt;

    public EffectiveMenuSnapshot(Long outletId, Long snapshotVersion, JsonNode effectiveMenu, Instant calculatedAt,
            Instant updatedAt) {

        if (outletId == null || outletId <= 0) {
            throw new IllegalArgumentException("Outlet Id must be not null and greater than 0");
        }

        if (snapshotVersion == null || snapshotVersion < 0) {
            throw new IllegalArgumentException("Snapshot version must be non-negative");

        }

        if (effectiveMenu == null) {
            throw new IllegalArgumentException("Menu cannot be null");

        }

        if (calculatedAt == null) {
            throw new IllegalArgumentException("calculatedAt cannot be null");

        }

        if (updatedAt == null) {
            throw new IllegalArgumentException("updatedAt cannot be null");

        }
        this.outletId = outletId;
        this.snapshotVersion = snapshotVersion;
        this.effectiveMenu = effectiveMenu;
        this.calculatedAt = calculatedAt;
        this.updatedAt = updatedAt;
    }

    public Long getOutletId() {
        return outletId;
    }

    public Long getSnapshotVersion() {
        return snapshotVersion;
    }

    public JsonNode getEffectiveMenu() {
        return effectiveMenu;
    }

    public Instant getCalculatedAt() {
        return calculatedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

}
