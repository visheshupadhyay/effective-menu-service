package com.vishesh.effectivemenu.snapshot.schedule;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.vishesh.effectivemenu.catalog.event.EventScheduleWindow;

/**
 * Evaluates recurring weekly windows using the outlet's local date and time.
 */
@Component
public final class WeeklyScheduleEvaluator {

    public boolean isActive(Map<String, List<EventScheduleWindow>> weeklyWindows,
            String outletTimezone, Instant currentInstant) {
        if (outletTimezone == null || outletTimezone.isBlank()) {
            throw new IllegalArgumentException("Outlet timezone is required");
        }
        if (currentInstant == null) {
            throw new IllegalArgumentException("Current instant is required");
        }

        final ZoneId zone;
        try {
            zone = ZoneId.of(outletTimezone);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Invalid outlet timezone: " + outletTimezone, exception);
        }

        if (weeklyWindows == null || weeklyWindows.isEmpty()) {
            return false;
        }

        ZonedDateTime localNow = currentInstant.atZone(zone);
        String day = localNow.getDayOfWeek().name();
        LocalTime localTime = localNow.toLocalTime();
        return weeklyWindows.getOrDefault(day, List.of()).stream()
                .anyMatch(window -> isInside(localTime, window));
    }

    /**
     * Clock-based overload keeps production calls and deterministic tests simple.
     */
    public boolean isActive(Map<String, List<EventScheduleWindow>> weeklyWindows,
            String outletTimezone, Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("Clock is required");
        }
        return isActive(weeklyWindows, outletTimezone, clock.instant());
    }

    private boolean isInside(LocalTime current, EventScheduleWindow window) {
        if (window == null || window.startTime() == null || window.endTime() == null) {
            return false;
        }
        return !current.isBefore(window.startTime()) && current.isBefore(window.endTime());
    }
}
