package com.svp.tracker.finance.predicts.dto;

import java.math.BigDecimal;

/**
 * One row in a leaderboard response ({@code /api/finance/predicts/leaderboard?type=...}).
 */
public record PredictsLeaderboardEntryDto(
        int rank,
        String symbol,
        int mentions24h,
        int uniqueAuthors24h,
        BigDecimal positivityPct,
        BigDecimal spikeZ,
        BigDecimal hotScore) {}
