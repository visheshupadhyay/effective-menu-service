package com.vishesh.effectivemenu.catalog.event.application;

/**
 * EffectiveMenuSnapshotNotFoundException
 */
/** Signals that an event references an outlet without a stored snapshot. */
public class EffectiveMenuSnapshotNotFoundException extends RuntimeException {

    public EffectiveMenuSnapshotNotFoundException(String message) {
        super(message);
    }

    public EffectiveMenuSnapshotNotFoundException(Long outletId) {
        super("Effective menu snapshot is not available for outlet " + outletId);
    }
    
}
