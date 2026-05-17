package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RobinhoodPerformanceSummaryDto(
        BigDecimal totalRealizedPnL,
        int winCount,
        int lossCount,
        int breakevenCount,
        /** Win rate among closed lots with non-zero P&amp;L, 0..1; 0 when no wins or losses. */
        double winRate,
        int tradingDays,
        LocalDate bestDay,
        BigDecimal bestDayPnL,
        LocalDate worstDay,
        BigDecimal worstDayPnL) {}
