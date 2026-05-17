package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record RobinhoodStrategyPerformanceDto(
        String strategy,
        BigDecimal totalRealizedPnL,
        int closedLots,
        double winRate) {}
