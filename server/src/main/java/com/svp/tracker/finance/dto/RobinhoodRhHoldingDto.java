package com.svp.tracker.finance.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
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
        @JsonAlias("unrealized_pnl_percent") BigDecimal unrealizedPnLPercent,
        /** Stable agentic position key (options: often instrumentId|leg|side). */
        @JsonAlias("position_key") String positionKey,
        @JsonAlias("chain_symbol") String chainSymbol,
        @JsonAlias("option_type") String optionType,
        @JsonAlias("strike_price") BigDecimal strikePrice,
        @JsonAlias("expiration_date") LocalDate expirationDate) {

    /** Convenience constructor for equity / legacy rows without option metadata. */
    public RobinhoodRhHoldingDto(
            String symbol,
            String positionType,
            BigDecimal quantity,
            BigDecimal averageBuyPrice,
            BigDecimal currentUnitPrice,
            BigDecimal marketValue,
            BigDecimal costBasis,
            BigDecimal unrealizedPnL,
            BigDecimal unrealizedPnLPercent) {
        this(
                symbol,
                positionType,
                quantity,
                averageBuyPrice,
                currentUnitPrice,
                marketValue,
                costBasis,
                unrealizedPnL,
                unrealizedPnLPercent,
                null,
                null,
                null,
                null,
                null);
    }
}
