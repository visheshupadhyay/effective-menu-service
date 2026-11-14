package com.vishesh.effectivemenu.snapshot.schedule;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Evaluates an optional [startDate, endDate) range in an outlet's timezone. */
public class SeasonalAvailabilityEvaluator {
    public boolean isActive(LocalDate startDate, LocalDate endDate, String timezone, Instant instant) {
        if (startDate == null && endDate == null) return true;
        if (startDate == null || endDate == null || !startDate.isBefore(endDate)) {
            throw new IllegalArgumentException("Seasonal range must have an increasing start and end date");
        }
        LocalDate localDate = instant.atZone(ZoneId.of(timezone)).toLocalDate();
        return !localDate.isBefore(startDate) && localDate.isBefore(endDate);
    }
}
