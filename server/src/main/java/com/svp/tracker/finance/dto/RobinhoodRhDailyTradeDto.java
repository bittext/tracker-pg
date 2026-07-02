package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** One executed Robinhood trade captured within a daily snapshot period. */
public record RobinhoodRhDailyTradeDto(
        String symbol,
        String side,
        String orderType,
        BigDecimal quantity,
        BigDecimal averagePrice,
        BigDecimal limitPrice,
        String state,
        Instant executedAt) {}
