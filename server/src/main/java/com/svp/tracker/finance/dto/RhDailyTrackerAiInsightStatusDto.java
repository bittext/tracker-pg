package com.svp.tracker.finance.dto;

/** GET /api/finance/robinhood/daily-tracker/ai-insights/status */
public record RhDailyTrackerAiInsightStatusDto(boolean enabled, boolean configured) {}
