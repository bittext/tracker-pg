package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record RobinhoodRhHoldingDto(
        String symbol,
        String positionType,
        BigDecimal quantity,
        /** Per-unit purchase price (share or option contract, matching Robinhood sync). */
        BigDecimal averageBuyPrice,
        /** Per-unit current market price in the same units as averageBuyPrice. */
        BigDecimal currentUnitPrice,
        BigDecimal marketValue,
        BigDecimal costBasis,
        BigDecimal unrealizedPnL,
        /** Unrealized P&amp;L as a percent of cost basis. */
        BigDecimal unrealizedPnLPercent) {}
