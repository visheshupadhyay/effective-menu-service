package com.vishesh.effectivemenu.catalog.event;

import java.math.BigDecimal;
import java.util.List;

/** Complete replacement structure for one bundle root in a Catalog event. */
public record EventBundleSnapshot(Long menuItemId, String name, BigDecimal price,
        List<EventBundleChild> children) {

    public EventBundleSnapshot {
        if (menuItemId == null || menuItemId <= 0) {
            throw new IllegalArgumentException("Bundle menu item ID must be positive");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Bundle name cannot be blank");
        }
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("Bundle price must be zero or greater");
        }
        if (children == null || children.stream().anyMatch(child -> child == null)) {
            throw new IllegalArgumentException("Bundle children must not contain null values");
        }

        name = name.trim();
        children = List.copyOf(children);
    }
}
