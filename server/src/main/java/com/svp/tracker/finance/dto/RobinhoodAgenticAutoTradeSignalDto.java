package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record RobinhoodAgenticAutoTradeSignalDto(
        String symbol,
        String side,
        String reason,
        BigDecimal overallPositivityPct,
        BigDecimal overallSpikeZ,
        int mentions24h,
        boolean acted,
        String actionResult) {}
