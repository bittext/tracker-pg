package com.svp.tracker.finance.dto;

/**
 * Near 52-week high with persistence (months -> year), optional fundamentals, heuristic growth copy, and a deep-link
 * for more information.
 */
public record Surge52WeekRowDto(
        String symbol,
        String shortName,
        Double regularMarketPrice,
        Double regularMarketChangePercent,
        Double fiftyTwoWeekHigh,
        Double fiftyTwoWeekHighChangePercent,
        double percentOf52WeekHigh,
        double momentumScore,
        int pastYearTradingDays,
        int daysNearRolling52WeekHigh,
        double pctPastYearNearRolling52WeekHigh,
        boolean repeatedStayAtTop,
        /** Total return from ~252 trading days ago to latest adjusted close (recent 52-week performance). */
        Double fiftyTwoWeekGainPercent,
        /** ~6 months of sessions evaluated for near rolling 52w high (126 trading days when available). */
        int pastSixMonthsTradingDays,
        int daysNearRolling52WeekHighSixMonths,
        double pctSixMonthsNearRolling52WeekHigh,
        Double marketCap,
        Double fiftyTwoWeekLow,
        Long averageDailyVolume3Month,
        Double trailingPe,
        Double forwardPe,
        /** Short label for UI chips (heuristic, not a rating). */
        String growthOutlookLabel,
        /** Plain-language growth / risk view from rules on available metrics. */
        String growthProspectsSummary,
        /** External quote page (Yahoo Finance). */
        String externalDetailUrl) {}
