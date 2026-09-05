package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** One filled Robinhood buy or sell from the Agentic synced-order history. */
public record RobinhoodExecutedTradeDto(
        String symbol,
        String side,
        String orderType,
        BigDecimal quantity,
        BigDecimal averagePrice,
        BigDecimal notional,
        String state,
        Instant executedAt,
        String accountSuffix,
        String accountLabel,
        BigDecimal realizedPnl,
        BigDecimal realizedPnlPercent) {}
