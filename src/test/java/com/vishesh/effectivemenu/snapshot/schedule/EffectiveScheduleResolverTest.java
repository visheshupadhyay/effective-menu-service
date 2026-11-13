package com.vishesh.effectivemenu.snapshot.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.vishesh.effectivemenu.catalog.event.EventScheduleWindow;

class EffectiveScheduleResolverTest {
    private final EffectiveScheduleResolver resolver = new EffectiveScheduleResolver();

    @Test
    void baseScheduleAppliesWhenOutletHasNoOverride() {
        Map<String, List<EventScheduleWindow>> result = resolver.resolve(
                Map.of("MONDAY", List.of(window(9, 0, 12, 0))), Map.of());

        assertEquals(List.of(window(9, 0, 12, 0)), result.get("MONDAY"));
    }

    @Test
    void outletAOverrideDoesNotChangeOutletBResolution() {
        Map<String, List<EventScheduleWindow>> base = Map.of("MONDAY", List.of(window(9, 0, 12, 0)));
        Map<String, List<EventScheduleWindow>> outletA = Map.of("MONDAY", List.of(window(17, 0, 20, 0)));

        assertEquals(List.of(window(17, 0, 20, 0)), resolver.resolve(base, outletA).get("MONDAY"));
        assertEquals(List.of(window(9, 0, 12, 0)), resolver.resolve(base, Map.of()).get("MONDAY"));
    }

    @Test
    void mondayOverrideDoesNotRemoveTuesdayInheritance() {
        Map<String, List<EventScheduleWindow>> base = Map.of(
                "MONDAY", List.of(window(9, 0, 12, 0)),
                "TUESDAY", List.of(window(10, 0, 13, 0)));
        Map<String, List<EventScheduleWindow>> override = Map.of("MONDAY", List.of(window(17, 0, 20, 0)));

        Map<String, List<EventScheduleWindow>> result = resolver.resolve(base, override);

        assertEquals(List.of(window(17, 0, 20, 0)), result.get("MONDAY"));
        assertEquals(List.of(window(10, 0, 13, 0)), result.get("TUESDAY"));
    }

    @Test
    void emptyOverrideEntryFallsBackToBaseInSparseStorageModel() {
        Map<String, List<EventScheduleWindow>> result = resolver.resolve(
                Map.of("MONDAY", List.of(window(9, 0, 12, 0))), Map.of("MONDAY", List.of()));

        assertEquals(List.of(window(9, 0, 12, 0)), result.get("MONDAY"));
    }

    @Test
    void noBaseScheduleProducesEmptyEffectiveSchedule() {
        assertEquals(List.of(), resolver.resolve(Map.of(), Map.of()).get("SUNDAY"));
    }

    private static EventScheduleWindow window(int startHour, int startMinute,
            int endHour, int endMinute) {
        return new EventScheduleWindow(LocalTime.of(startHour, startMinute), LocalTime.of(endHour, endMinute));
    }
}
