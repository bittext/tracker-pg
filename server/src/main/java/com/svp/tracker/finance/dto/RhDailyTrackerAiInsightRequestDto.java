package com.svp.tracker.finance.dto;

import java.util.List;

/** POST /api/finance/robinhood/daily-tracker/ai-insights */
public record RhDailyTrackerAiInsightRequestDto(
        String scope,
        Integer year,
        Integer month,
        /** ISO date (yyyy-MM-dd) — Monday of the ISO week, or any day in the week. */
        String weekStart,
        /** ISO date (yyyy-MM-dd) for DAY scope. */
        String day,
        Boolean forceRefresh) {}
