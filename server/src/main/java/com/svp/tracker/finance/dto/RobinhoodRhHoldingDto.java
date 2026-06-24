package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record RobinhoodRhHoldingDto(
        String symbol,
        String positionType,
        BigDecimal quantity,
        BigDecimal averageBuyPrice,
        BigDecimal marketValue,
        BigDecimal costBasis,
        BigDecimal unrealizedPnL) {}
