package com.vishesh.effectivemenu.snapshot.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.vishesh.effectivemenu.catalog.event.EventBundleChild;
import com.vishesh.effectivemenu.catalog.event.EventBundleSnapshot;
import com.vishesh.effectivemenu.catalog.event.EventModifierGroup;
import com.vishesh.effectivemenu.catalog.event.EventModifierOption;
import com.vishesh.effectivemenu.catalog.event.EventScheduleWindow;
import com.vishesh.effectivemenu.catalog.event.MenuItemStatus;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Writes the common internal JSON shape for one Effective Menu item.
 *
 * <p>This class deliberately does not decide whether an event is duplicate or
 * stale, nor does it save snapshots. Event handlers make those workflow
 * decisions; this mapper only keeps the stored item structure consistent.</p>
 */
public final class EffectiveMenuItemSnapshotMapper {

    /** Creates a complete base JSON item for a newly onboarded outlet. */
    public ObjectNode createItem(Long itemId, String name, BigDecimal price,
            boolean availability, boolean visibility, MenuItemStatus status, Long catalogVersion) {
        ObjectNode item = JsonNodeFactory.instance.objectNode();
        item.put("id", itemId);
        item.put("name", name);
        item.put("price", price);
        item.put("availability", availability);
        item.put("visibility", visibility);
        item.put("status", status.name());
        item.put("_catalogVersion", catalogVersion);
        return item;
    }

    /** Replaces the complete modifier configuration, including intentional removals. */
    public void replaceModifierGroups(ObjectNode item, List<EventModifierGroup> groups) {
        if (groups.isEmpty()) {
            // An empty complete configuration means the item now has no modifiers.
            item.remove("modifierGroups");
            return;
        }

        ArrayNode groupsNode = item.putArray("modifierGroups");
        for (EventModifierGroup group : groups) {
            ObjectNode groupNode = groupsNode.addObject();
            groupNode.put("groupId", group.groupId());
            groupNode.put("groupName", group.groupName());
            groupNode.put("minimumSelections", group.minimumSelections());
            groupNode.put("maximumSelections", group.maximumSelections());
            groupNode.put("displayOrder", group.displayOrder());

            ArrayNode optionsNode = groupNode.putArray("options");
            for (EventModifierOption option : group.options()) {
                ObjectNode optionNode = optionsNode.addObject();
                optionNode.put("optionId", option.optionId());
                optionNode.put("optionName", option.optionName());
                optionNode.put("priceDelta", option.priceDelta());
                optionNode.put("displayOrder", option.displayOrder());
            }
        }
    }

    /** Replaces a bundle's complete tree so removed children cannot survive. */
    public void replaceBundleStructure(ObjectNode item, EventBundleSnapshot bundle,
            Boolean eventAvailability, Boolean eventVisibility) {
        item.put("name", bundle.name());
        item.put("price", bundle.price());

        boolean ownAvailability = eventAvailability != null
                ? eventAvailability
                : item.path("_bundleOwnAvailability").asBoolean(item.path("availability").asBoolean(true));
        boolean ownVisibility = eventVisibility != null
                ? eventVisibility
                : item.path("_bundleOwnVisibility").asBoolean(item.path("visibility").asBoolean(true));
        item.put("_bundleOwnAvailability", ownAvailability);
        item.put("_bundleOwnVisibility", ownVisibility);

        BundleEligibility childrenEligibility = replaceBundleChildren(item, bundle.children());
        item.put("availability", ownAvailability && childrenEligibility.available());
        item.put("visibility", ownVisibility && childrenEligibility.visible());
    }

    /** Stores schedule data so the customer read path can re-evaluate time later. */
    public void storeSchedule(ObjectNode item, Map<String, List<EventScheduleWindow>> windows,
            String timezone, Boolean catalogAvailability) {
        ObjectNode schedule = item.putObject("_schedule");
        schedule.put("timezone", timezone);
        schedule.put("catalogAvailability", catalogAvailability == null || Boolean.TRUE.equals(catalogAvailability));
        ObjectNode windowsNode = schedule.putObject("windows");
        windows.forEach((weekday, dayWindows) -> {
            ArrayNode dayNode = windowsNode.putArray(weekday);
            dayWindows.forEach(window -> {
                ObjectNode windowNode = dayNode.addObject();
                windowNode.put("startTime", window.startTime().toString());
                windowNode.put("endTime", window.endTime().toString());
            });
        });
    }

    public void updateStoredCatalogAvailability(ObjectNode item, Boolean availability) {
        JsonNode scheduleNode = item.get("_schedule");
        if (scheduleNode instanceof ObjectNode schedule) {
            schedule.put("catalogAvailability", Boolean.TRUE.equals(availability));
        }
    }

    public void storeSeasonalAvailability(ObjectNode item, LocalDate startDate,
            LocalDate endDate, String timezone) {
        ObjectNode seasonal = item.putObject("_seasonalAvailability");
        seasonal.put("timezone", timezone);
        if (startDate != null) {
            seasonal.put("startDate", startDate.toString());
        }
        if (endDate != null) {
            seasonal.put("endDate", endDate.toString());
        }
    }

    public boolean readCatalogAvailability(ObjectNode item, Boolean eventAvailability) {
        if (eventAvailability != null) {
            return Boolean.TRUE.equals(eventAvailability);
        }
        JsonNode schedule = item.get("_schedule");
        return schedule == null || schedule.path("catalogAvailability").asBoolean(true);
    }

    public void updateStoredBundleAvailability(ObjectNode item, Boolean availability) {
        if (item.has("bundleChildren")) {
            item.put("_bundleOwnAvailability", Boolean.TRUE.equals(availability));
        }
    }

    public void updateStoredBundleVisibility(ObjectNode item, Boolean visibility) {
        if (item.has("bundleChildren")) {
            item.put("_bundleOwnVisibility", Boolean.TRUE.equals(visibility));
        }
    }

    private BundleEligibility replaceBundleChildren(ObjectNode parent, List<EventBundleChild> children) {
        ArrayNode childrenNode = parent.putArray("bundleChildren");
        boolean everyChildVisible = true;
        boolean everyChildAvailable = true;

        for (EventBundleChild child : children) {
            ObjectNode childNode = childrenNode.addObject();
            childNode.put("id", child.menuItemId());
            childNode.put("name", child.name());
            childNode.put("quantity", child.quantity());

            ObjectNode eligibility = childNode.putObject("_bundleEligibility");
            eligibility.put("status", child.status().name());
            eligibility.put("visibility", child.visibility());
            eligibility.put("availability", child.availability());

            BundleEligibility nestedEligibility = child.children().isEmpty()
                    ? BundleEligibility.eligible()
                    : replaceBundleChildren(childNode, child.children());
            boolean childVisible = child.visibility()
                    && child.status() != MenuItemStatus.ARCHIVED
                    && child.status() != MenuItemStatus.HIDDEN
                    && child.status() != MenuItemStatus.DRAFT
                    && nestedEligibility.visible();
            boolean childAvailable = child.availability() && nestedEligibility.available();

            everyChildVisible &= childVisible;
            everyChildAvailable &= childAvailable;
        }
        return new BundleEligibility(everyChildVisible, everyChildAvailable);
    }

    private record BundleEligibility(boolean visible, boolean available) {
        private static BundleEligibility eligible() {
            return new BundleEligibility(true, true);
        }
    }
}
