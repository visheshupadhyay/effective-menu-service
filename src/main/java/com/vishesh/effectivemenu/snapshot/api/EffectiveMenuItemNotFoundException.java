package com.vishesh.effectivemenu.snapshot.api;

public class EffectiveMenuItemNotFoundException extends RuntimeException {
    public EffectiveMenuItemNotFoundException(Long menuItemId, Long outletId) {
        super("Effective menu item " + menuItemId + " was not found for outlet " + outletId);
    }
}
