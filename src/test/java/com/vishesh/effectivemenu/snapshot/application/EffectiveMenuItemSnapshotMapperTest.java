package com.vishesh.effectivemenu.snapshot.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.vishesh.effectivemenu.catalog.event.EventBundleChild;
import com.vishesh.effectivemenu.catalog.event.EventBundleSnapshot;
import com.vishesh.effectivemenu.catalog.event.EventModifierGroup;
import com.vishesh.effectivemenu.catalog.event.EventModifierOption;
import com.vishesh.effectivemenu.catalog.event.EventScheduleWindow;
import com.vishesh.effectivemenu.catalog.event.MenuItemKind;
import com.vishesh.effectivemenu.catalog.event.MenuItemStatus;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

class EffectiveMenuItemSnapshotMapperTest {
    private final EffectiveMenuItemSnapshotMapper mapper = new EffectiveMenuItemSnapshotMapper();

    @Test
    void createItem_writesBusinessFieldsAndCatalogVersion() {
        ObjectNode item = mapper.createItem(101L, "Burger", new BigDecimal("120.00"),
                true, true, MenuItemStatus.ACTIVE, 8L);

        assertEquals(101L, item.path("id").asLong());
        assertEquals("Burger", item.path("name").asText());
        assertEquals(120, item.path("price").asInt());
        assertEquals("ACTIVE", item.path("status").asText());
        assertEquals(8L, item.path("_catalogVersion").asLong());
    }

    @Test
    void storeSchedule_writesInternalScheduleMetadata() {
        ObjectNode item = JsonNodeFactory.instance.objectNode();

        mapper.storeSchedule(item, Map.of(
                "MONDAY", List.of(new EventScheduleWindow(LocalTime.of(9, 0), LocalTime.of(22, 0)))),
                "Asia/Kolkata", true);

        assertEquals("Asia/Kolkata", item.path("_schedule").path("timezone").asText());
        assertEquals(true, item.path("_schedule").path("catalogAvailability").asBoolean());
        assertEquals("09:00", item.path("_schedule").path("windows").path("MONDAY")
                .get(0).path("startTime").asText());
    }

    @Test
    void replaceModifierGroups_replacesOldConfigurationAndEmptyListRemovesIt() {
        ObjectNode item = JsonNodeFactory.instance.objectNode();
        item.putArray("modifierGroups").addObject().put("groupName", "Old Extras");
        EventModifierGroup extras = new EventModifierGroup(5L, "Choose Extras", 0, 1, 0,
                List.of(new EventModifierOption(11L, "Cheese", new BigDecimal("20.00"), 0)));

        mapper.replaceModifierGroups(item, List.of(extras));

        assertEquals(1, item.path("modifierGroups").size());
        assertEquals("Choose Extras", item.path("modifierGroups").get(0).path("groupName").asText());
        assertEquals("Cheese", item.path("modifierGroups").get(0).path("options").get(0)
                .path("optionName").asText());

        mapper.replaceModifierGroups(item, List.of());

        assertNull(item.get("modifierGroups"));
    }

    @Test
    void replaceBundleStructure_replacesOldChildrenAndPreservesEligibilityRules() {
        ObjectNode item = mapper.createItem(101L, "Old Meal", new BigDecimal("199.00"),
                true, true, MenuItemStatus.ACTIVE, 8L);
        item.putArray("bundleChildren").addObject().put("id", 999L);
        EventBundleChild unavailableFries = new EventBundleChild(102L, "Fries", MenuItemKind.PRODUCT,
                1, 0, MenuItemStatus.ACTIVE, true, false, List.of());
        EventBundleSnapshot meal = new EventBundleSnapshot(101L, "Burger Meal", new BigDecimal("299.00"),
                List.of(unavailableFries));

        mapper.replaceBundleStructure(item, meal, true, true);

        assertEquals("Burger Meal", item.path("name").asText());
        assertEquals(1, item.path("bundleChildren").size());
        assertEquals("Fries", item.path("bundleChildren").get(0).path("name").asText());
        assertFalse(item.path("availability").asBoolean());
        assertEquals(true, item.path("visibility").asBoolean());
    }

    @Test
    void storeSeasonalAvailability_writesDatesAndTimezone() {
        ObjectNode item = JsonNodeFactory.instance.objectNode();

        mapper.storeSeasonalAvailability(item, LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30), "Asia/Kolkata");

        assertEquals("Asia/Kolkata", item.path("_seasonalAvailability").path("timezone").asText());
        assertEquals("2026-11-01", item.path("_seasonalAvailability").path("startDate").asText());
        assertEquals("2026-11-30", item.path("_seasonalAvailability").path("endDate").asText());
    }
}
