package com.svp.tracker.admin.dto.usage;

/** A single day's count for one feature (used to drive the stacked time-series chart). */
public record DailyActivityPointDto(
        /** ISO local date in UTC (e.g. {@code 2026-05-11}). */
        String day,
        String feature,
        long count) {}
