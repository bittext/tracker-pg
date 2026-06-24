package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Agentic portfolio return vs S&amp;P 500 ({@code ^GSPC}) since tracker cutoff. */
public record RobinhoodAgenticSpxComparisonDto(
        String agenticAccountMasked,
        Instant trackingStartedAt,
        String spxSymbol,
        LocalDate spxStartDate,
        BigDecimal spxStartPrice,
        BigDecimal spxCurrentPrice,
        BigDecimal spxReturnPct,
        BigDecimal agenticMarketValue,
        BigDecimal agenticCostBasis,
        BigDecimal agenticBaselineMarketValue,
        BigDecimal agenticReturnPct,
        String agenticReturnBasis,
        Instant agenticSyncedAt) {}
