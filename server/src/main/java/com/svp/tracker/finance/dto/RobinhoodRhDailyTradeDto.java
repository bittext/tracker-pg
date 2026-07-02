package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One executed Robinhood trade captured within a daily snapshot period.
 * {@code accountSuffix}/{@code accountLabel} are set only for the consolidated day-level list.
 */
public record RobinhoodRhDailyTradeDto(
        String symbol,
        String side,
        String orderType,
        BigDecimal quantity,
        BigDecimal averagePrice,
        BigDecimal limitPrice,
        String state,
        Instant executedAt,
        String accountSuffix,
        String accountLabel) {

    public RobinhoodRhDailyTradeDto(
            String symbol,
            String side,
            String orderType,
            BigDecimal quantity,
            BigDecimal averagePrice,
            BigDecimal limitPrice,
            String state,
            Instant executedAt) {
        this(symbol, side, orderType, quantity, averagePrice, limitPrice, state, executedAt, null, null);
    }

    public RobinhoodRhDailyTradeDto withAccount(String accountSuffix, String accountLabel) {
        return new RobinhoodRhDailyTradeDto(
                symbol, side, orderType, quantity, averagePrice, limitPrice, state, executedAt, accountSuffix, accountLabel);
    }
}
