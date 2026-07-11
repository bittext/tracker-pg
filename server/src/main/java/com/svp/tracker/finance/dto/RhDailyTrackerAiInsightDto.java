package com.svp.tracker.finance.dto;

import java.time.Instant;
import java.util.List;

/** Structured coaching insight for Daily Tracker activity (not realized P&L). */
public record RhDailyTrackerAiInsightDto(
        String scope,
        String periodKey,
        String periodLabel,
        Instant generatedAt,
        String model,
        boolean cached,
        String summary,
        List<String> leanings,
        List<String> trends,
        List<String> improvements,
        List<String> nextActions,
        RhDailyTrackerAiFactsDigestDto factsDigest) {}
