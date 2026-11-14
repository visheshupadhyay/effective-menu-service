package com.vishesh.effectivemenu.snapshot.schedule;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SeasonalAvailabilityEvaluatorTest {
    private final SeasonalAvailabilityEvaluator evaluator = new SeasonalAvailabilityEvaluator();

    @Test
    void startIsInclusiveAndEndIsExclusiveInOutletTimezone() {
        LocalDate start = LocalDate.of(2026, 10, 15);
        LocalDate end = LocalDate.of(2026, 11, 5);
        assertTrue(evaluator.isActive(start, end, "Europe/Berlin", Instant.parse("2026-10-14T22:00:00Z")));
        assertFalse(evaluator.isActive(start, end, "Europe/Berlin", Instant.parse("2026-11-04T23:00:00Z")));
    }

    @Test
    void absentRangeMeansNoSeasonalRestriction() {
        assertTrue(evaluator.isActive(null, null, "UTC", Instant.parse("2026-01-01T00:00:00Z")));
    }
}
