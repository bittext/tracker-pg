package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record RobinhoodRhHoldingDto(
        String symbol,
        String positionType,
        BigDecimal quantity,
/** Per-unit purchase price (per share for equities; per share of underlying for options). */
        BigDecimal averageBuyPrice,
        /** Per-unit current market price in the same units as averageBuyPrice. */
        BigDecimal currentUnitPrice,
        BigDecimal marketValue,
        BigDecimal costBasis,
        BigDecimal unrealizedPnL,
        /** Unrealized P&amp;L as a percent of cost basis. */
        BigDecimal unrealizedPnLPercent) {}
