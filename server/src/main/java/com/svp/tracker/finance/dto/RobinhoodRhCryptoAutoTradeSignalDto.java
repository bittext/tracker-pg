package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record RobinhoodRhCryptoAutoTradeSignalDto(
        String symbol,
        String tradingPair,
        String side,
        String reason,
        BigDecimal overallPositivityPct,
        BigDecimal overallSpikeZ,
        int mentions24h,
        boolean acted,
        String result) {}
