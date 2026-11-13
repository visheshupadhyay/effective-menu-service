package com.vishesh.effectivemenu.catalog.event;

import java.time.LocalTime;

/** Schedule window transported inside a Catalog event. */
public record EventScheduleWindow(LocalTime startTime, LocalTime endTime) { }
