package com.vishesh.effectivemenu.snapshot.persistence;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import tools.jackson.databind.JsonNode;

@Entity
@Table(name = "effective_menu_snapshot")
/** Database row that stores one outlet snapshot as JSON. */
public class EffectiveMenuSnapshotEntity {
    @Id
    @Column(name = "outlet_id", nullable = false)
    private Long outletId;

    @Column(name = "snapshot_version", nullable = false)
    private Long snapshotVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "effective_menu", nullable = false, columnDefinition = "jsonb")
    private JsonNode effectiveMenu;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EffectiveMenuSnapshotEntity() {
    }

    public EffectiveMenuSnapshotEntity(Long outletId, Long snapshotVersion, JsonNode effectiveMenu,
            Instant calculatedAt, Instant updatedAt) {

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
