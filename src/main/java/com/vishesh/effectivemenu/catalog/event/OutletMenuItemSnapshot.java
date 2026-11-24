package com.vishesh.effectivemenu.catalog.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * One fully resolved item inside an outlet-initialization event.
 *
 * <p>All customer-relevant values describe the item's starting state; they do
 * not mean "leave the existing value unchanged" as sparse update fields do.
 */
public record OutletMenuItemSnapshot(
        Long menuItemId,
        Long menuItemVersion,
        String itemName,
        BigDecimal price,
        MenuItemStatus status,
        Boolean availability,
        Boolean visibility,
        Map<String, List<EventScheduleWindow>> baseScheduleWindows,
        Map<String, List<EventScheduleWindow>> outletScheduleWindows,
        LocalDate seasonalStartDate,
        LocalDate seasonalEndDate,
        List<EventModifierGroup> modifierGroups,
        EventBundleSnapshot bundleSnapshot) {

    public OutletMenuItemSnapshot {
        if (menuItemId == null || menuItemId <= 0) {
            throw new IllegalArgumentException("Menu item ID must be positive");
        }
        if (menuItemVersion == null || menuItemVersion < 0) {
            throw new IllegalArgumentException("Menu item version must be zero or greater");
        }
        if (itemName == null || itemName.isBlank()) {
            throw new IllegalArgumentException("Menu item name is required");
        }
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("Menu item price must be zero or greater");
        }
        if (status == null || availability == null || visibility == null) {
            throw new IllegalArgumentException("Menu item status, availability, and visibility are required");
        }
        if ((seasonalStartDate == null) != (seasonalEndDate == null)
                || (seasonalStartDate != null && !seasonalStartDate.isBefore(seasonalEndDate))) {
            throw new IllegalArgumentException("Seasonal availability dates must be an increasing complete range");
        }
        if (bundleSnapshot != null && !menuItemId.equals(bundleSnapshot.menuItemId())) {
            throw new IllegalArgumentException("Bundle snapshot must belong to the menu item");
        }

        itemName = itemName.trim();
        baseScheduleWindows = immutableWindows(baseScheduleWindows);
        outletScheduleWindows = immutableWindows(outletScheduleWindows);
        modifierGroups = modifierGroups == null ? List.of() : List.copyOf(modifierGroups);
    }

    private static Map<String, List<EventScheduleWindow>> immutableWindows(
            Map<String, List<EventScheduleWindow>> windows) {
        if (windows == null || windows.isEmpty()) {
            return Map.of();
        }
        return windows.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> entry.getValue() == null ? List.of() : List.copyOf(entry.getValue())));
    }
}
