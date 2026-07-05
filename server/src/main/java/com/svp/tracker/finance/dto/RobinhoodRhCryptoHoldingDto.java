package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

/** One coin row within a crypto snapshot (future use). */
public record RobinhoodRhCryptoHoldingDto(
        String symbol,
        BigDecimal quantity,
        BigDecimal averageBuyPrice,
        BigDecimal currentUnitPrice,
        BigDecimal marketValue,
        BigDecimal costBasis,
        BigDecimal unrealizedPnL,
        BigDecimal unrealizedPnLPercent) {}
