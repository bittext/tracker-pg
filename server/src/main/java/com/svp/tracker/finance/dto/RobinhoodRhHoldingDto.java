package com.svp.tracker.finance.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.math.BigDecimal;

public record RobinhoodRhHoldingDto(
        String symbol,
        @JsonAlias("position_type") String positionType,
        BigDecimal quantity,
        /** Per-unit purchase price (per share for equities; per share of underlying for options). */
        @JsonAlias("average_buy_price") BigDecimal averageBuyPrice,
        /** Per-unit current market price in the same units as averageBuyPrice. */
        @JsonAlias("current_unit_price") BigDecimal currentUnitPrice,
        @JsonAlias("market_value") BigDecimal marketValue,
        @JsonAlias("cost_basis") BigDecimal costBasis,
        @JsonAlias("unrealized_pnl") BigDecimal unrealizedPnL,
        /** Unrealized P&amp;L as a percent of cost basis. */
        @JsonAlias("unrealized_pnl_percent") BigDecimal unrealizedPnLPercent) {}
