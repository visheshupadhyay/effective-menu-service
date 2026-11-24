package com.vishesh.effectivemenu.catalog.event;

import java.util.List;

/**
 * One resolved child carried by a complete bundle event. The lifecycle fields
 * are event-internal data used to decide whether the parent bundle is safe to
 * show; the customer response exposes only the lightweight child summary.
 */
public record EventBundleChild(Long menuItemId, String name, MenuItemKind kind,
        int quantity, int displayOrder, MenuItemStatus status, boolean visibility,
        boolean availability, List<EventBundleChild> children) {

    public EventBundleChild {
        if (menuItemId == null || menuItemId <= 0) {
            throw new IllegalArgumentException("Bundle child menu item ID must be positive");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Bundle child name cannot be blank");
        }
        if (kind == null) {
            throw new IllegalArgumentException("Bundle child kind is required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Bundle child quantity must be positive");
        }
        if (displayOrder < 0) {
            throw new IllegalArgumentException("Bundle child display order cannot be negative");
        }
        if (status == null) {
            throw new IllegalArgumentException("Bundle child status is required");
        }
        if (children == null || children.stream().anyMatch(child -> child == null)) {
            throw new IllegalArgumentException("Bundle child list must not contain null values");
        }

        name = name.trim();
        children = List.copyOf(children);
    }
}
