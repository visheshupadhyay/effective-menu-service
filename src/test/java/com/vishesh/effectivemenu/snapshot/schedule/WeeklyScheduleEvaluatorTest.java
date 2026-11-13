package com.vishesh.effectivemenu.snapshot.schedule;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.vishesh.effectivemenu.catalog.event.EventScheduleWindow;

class WeeklyScheduleEvaluatorTest {
    private final WeeklyScheduleEvaluator evaluator = new WeeklyScheduleEvaluator();

    @Test
    void insideWindow_isActive() {
        assertTrue(evaluator.isActive(windows("MONDAY", 9, 0, 11, 0), "UTC",
                Instant.parse("2026-01-05T10:00:00Z")));
    }

    @Test
    void beforeAndAfterWindow_areInactive() {
        Map<String, List<EventScheduleWindow>> windows = windows("MONDAY", 9, 0, 11, 0);
        assertFalse(evaluator.isActive(windows, "UTC", Instant.parse("2026-01-05T08:59:59Z")));
        assertFalse(evaluator.isActive(windows, "UTC", Instant.parse("2026-01-05T11:00:00Z")));
    }

    @Test
    void twoWindowsOnOneDay_matchesEitherWindow() {
        Map<String, List<EventScheduleWindow>> windows = Map.of("MONDAY", List.of(
                window(9, 0, 11, 0), window(17, 0, 19, 0)));
        assertTrue(evaluator.isActive(windows, "UTC", Instant.parse("2026-01-05T17:30:00Z")));
        assertFalse(evaluator.isActive(windows, "UTC", Instant.parse("2026-01-05T13:00:00Z")));
    }

    @Test
    void convertsInstantToOutletTimezone() {
        assertTrue(evaluator.isActive(windows("MONDAY", 5, 0, 6, 0), "America/New_York",
                Instant.parse("2026-01-05T10:30:00Z")));
    }

    @Test
    void daylightSavingTransition_usesZoneRules() {
        // 01:30 UTC becomes 03:30 in Berlin on the spring-forward Sunday.
        assertTrue(evaluator.isActive(windows("SUNDAY", 3, 0, 4, 0), "Europe/Berlin",
                Instant.parse("2026-03-29T01:30:00Z")));
    }

    @Test
    void sundayWindow_isEvaluated() {
        assertTrue(evaluator.isActive(windows("SUNDAY", 10, 0, 11, 0), "UTC",
                Instant.parse("2026-01-04T10:00:00Z")));
    }

    @Test
    void midnightBoundary_isStartInclusiveAndEndExclusive() {
        Map<String, List<EventScheduleWindow>> windows = windows("MONDAY", 0, 0, 1, 0);
        assertTrue(evaluator.isActive(windows, "UTC", Instant.parse("2026-01-05T00:00:00Z")));
        assertFalse(evaluator.isActive(windows, "UTC", Instant.parse("2026-01-05T01:00:00Z")));
    }

    @Test
    void invalidTimezone_isRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> evaluator.isActive(Map.of(), "Not/AZone", Instant.EPOCH));
    }

    private static Map<String, List<EventScheduleWindow>> windows(String day,
            int startHour, int startMinute, int endHour, int endMinute) {
        return Map.of(day, List.of(window(startHour, startMinute, endHour, endMinute)));
    }

    private static EventScheduleWindow window(int startHour, int startMinute,
            int endHour, int endMinute) {
        return new EventScheduleWindow(LocalTime.of(startHour, startMinute), LocalTime.of(endHour, endMinute));
    }
}
