package com.svp.tracker.finance.predicts.dto;

import java.math.BigDecimal;

/**
 * Per-source per-ticker rollup used by the summary endpoint and the leaderboard.
 *
 * @param positivityPct rolling sentiment ratio in {@code [-100, +100]} ({@code (pos − neg) / (pos + neg + neu)} × 100)
 * @param spikeZ z-score of current 1h mention count vs the hour-of-week baseline (NaN-safe; 0 when baseline empty)
 * @param surgeZ z-score of unique-author count
 * @param mentions24h mentions in the last 24h for this (symbol, source)
 * @param uniqueAuthors24h distinct author count in the last 24h for this (symbol, source)
 */
public record PredictsSourceSummaryDto(
        String source,
        int mentions24h,
        int uniqueAuthors24h,
        int posCount24h,
        int negCount24h,
        int neuCount24h,
        BigDecimal sentimentAvg24h,
        BigDecimal positivityPct,
        BigDecimal spikeZ,
        BigDecimal surgeZ) {}
