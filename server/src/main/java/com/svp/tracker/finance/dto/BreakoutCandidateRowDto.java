package com.svp.tracker.finance.dto;

/**
 * One name flagged by the breakout-style scan (heuristic technical setup from Yahoo daily OHLCV + quote). Not
 * investment advice.
 */
public record BreakoutCandidateRowDto(
        String symbol,
        String shortName,
        Double regularMarketPrice,
        Double regularMarketChangePercent,
        Double percentOf52WeekHigh,
        /** 0–100 composite heuristic score (higher = more setup signals aligned). */
        double breakoutScore,
        /** Short machine-readable pattern label for the row. */
        String patternLabel,
        /** Human-readable explanation of why the row scored. */
        String rationale,
        /** Price as % of the ~20-session prior resistance high (can exceed 100 if breaking). */
        Double pctOfRecentResistance,
        /** Last 5 sessions avg volume ÷ prior 20 sessions avg (null if unavailable). */
        Double volumeRatioVs20d,
        /** Mean true range last 10 days ÷ mean TR days 11–30 (null if unavailable); below 1 suggests contraction. */
        Double atrCompressionRatio,
        /** Price vs 50-session SMA (% above/below), null if SMA not computable. */
        Double pctVsSma50,
        String externalDetailUrl) {}
