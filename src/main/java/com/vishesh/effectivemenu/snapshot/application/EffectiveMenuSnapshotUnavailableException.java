package com.vishesh.effectivemenu.snapshot.application;

/** Signals that an outlet has no customer-ready snapshot yet. */
public class EffectiveMenuSnapshotUnavailableException extends RuntimeException {

    private final Long outletId;

    public EffectiveMenuSnapshotUnavailableException(Long outletId) {
        super("Effective menu is not available for outlet " + outletId);
        this.outletId = outletId;
    }

    public Long getOutletId() {
        return outletId;
    }
}
