package com.vishesh.effectivemenu.snapshot.api;

import java.math.BigDecimal;

/** Small internal response used by Catalog reconciliation. */
public record EffectiveMenuItemStateResponse(
        Long menuItemId,
        Long outletId,
        BigDecimal price,
        boolean availability,
        boolean visibility,
        Long version) {
}
