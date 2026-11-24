package com.vishesh.effectivemenu.catalog.event;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable complete snapshot of one modifier group and its selectable options.
 */
public record EventModifierGroup(Long groupId, String groupName, int minimumSelections,
        int maximumSelections, int displayOrder, List<EventModifierOption> options) {
    public EventModifierGroup {
        if (groupId == null || groupId <= 0) {
            throw new IllegalArgumentException("Modifier group ID must be positive");
        }
        if (groupName == null || groupName.isBlank()) {
            throw new IllegalArgumentException("Modifier group name cannot be blank");
        }
        if (minimumSelections < 0 || maximumSelections < minimumSelections) {
            throw new IllegalArgumentException("Modifier group selection limits are invalid");
        }
        if (displayOrder < 0) {
            throw new IllegalArgumentException("Modifier group display order cannot be negative");
        }
        if (options == null || options.isEmpty()) {
            throw new IllegalArgumentException("A modifier group event must contain at least one option");
        }
        if (maximumSelections > options.size()) {
            throw new IllegalArgumentException("Maximum selections cannot exceed the number of options");
        }

        Set<Integer> optionDisplayOrders = new HashSet<>();
        for (EventModifierOption option : options) {
            if (option == null) {
                throw new IllegalArgumentException("Modifier group options cannot contain null values");
            }
            if (!optionDisplayOrders.add(option.displayOrder())) {
                throw new IllegalArgumentException("Modifier option display orders must be unique");
            }
        }

        groupName = groupName.trim();
        options = List.copyOf(options);
    }
}
