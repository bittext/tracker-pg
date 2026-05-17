package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Portfolio-level summary aligned with Robinhood Individual investing (value, cash, P&amp;L). May come from a
 * snapshot file or FIFO + quotes.
 */
public record RobinhoodPortfolioOverviewDto(
        LocalDate asOfDate,
        /** {@code snapshot}, {@code computed}, or {@code snapshot+computed}. */
        String source,
        BigDecimal portfolioValue,
        BigDecimal cash,
        BigDecimal todayPnL,
        Double todayPnLPercent,
        BigDecimal ytdTotalPnL,
        BigDecimal todayRealizedPnL,
        BigDecimal ytdRealizedPnL,
        BigDecimal openUnrealizedPnL,
        List<RobinhoodPortfolioPositionDto> positions,
        String note) {}
