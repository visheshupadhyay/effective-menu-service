package com.vishesh.effectivemenu.snapshot.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Builds the one consistent Redis key used for an outlet's menu snapshot. */
@Component
public class EffectiveMenuCacheKey {
    private final String keyPrefix;

    public EffectiveMenuCacheKey(
            @Value("${app.effective-menu-cache.key-prefix}") String keyPrefix) {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("Cache key prefix must not be blank");
        }
        this.keyPrefix = keyPrefix;
    }

    public String createKey(Long outletId) {
        if (outletId == null || outletId <= 0) {
            throw new IllegalArgumentException("Outlet ID must be positive");
        }
        return keyPrefix + outletId;
    }
}
