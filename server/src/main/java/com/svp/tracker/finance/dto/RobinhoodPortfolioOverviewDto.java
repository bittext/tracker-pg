package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Portfolio-level summary from FIFO + quotes (Reports → Robinhood API payload). */
public record RobinhoodPortfolioOverviewDto(
        LocalDate asOfDate,
        /** {@code computed}. */
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
