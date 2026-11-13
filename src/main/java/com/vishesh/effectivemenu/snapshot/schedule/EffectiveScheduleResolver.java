package com.vishesh.effectivemenu.snapshot.schedule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.vishesh.effectivemenu.catalog.event.EventScheduleWindow;

/** Applies sparse outlet weekday overrides on top of a base weekly schedule. */
@Component
public final class EffectiveScheduleResolver {
    private static final List<String> WEEKDAYS = List.of(
            "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");

    public Map<String, List<EventScheduleWindow>> resolve(
            Map<String, List<EventScheduleWindow>> baseSchedule,
            Map<String, List<EventScheduleWindow>> outletOverride) {
        Map<String, List<EventScheduleWindow>> base = baseSchedule == null ? Map.of() : baseSchedule;
        Map<String, List<EventScheduleWindow>> override = outletOverride == null ? Map.of() : outletOverride;
        Map<String, List<EventScheduleWindow>> result = new LinkedHashMap<>();

        for (String weekday : WEEKDAYS) {
            List<EventScheduleWindow> outletWindows = override.get(weekday);
            // Empty entries cannot be persisted as rows, so they mean no override
            // in the current sparse-window storage model.
            List<EventScheduleWindow> windows = outletWindows != null && !outletWindows.isEmpty()
                    ? outletWindows
                    : base.getOrDefault(weekday, List.of());
            result.put(weekday, windows == null ? List.of() : List.copyOf(new ArrayList<>(windows)));
        }
        return Map.copyOf(result);
    }
}
