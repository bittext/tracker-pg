package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One account's (or the combined) figures for one quarter. The income statement, performance (P&amp;L)
 * statement and cash flow statement are all drawn from these numbers. Null means "not known", not zero.
 */
public record RobinhoodStatementDto(
        String suffix,
        String label,
        /** Close the quarter is measured from (last close before it, or the first close when tracking began later). */
        LocalDate openingDate,
        LocalDate closingDate,
        BigDecimal openingValue,
        BigDecimal closingValue,
        /** Money in from outside (banks, spending). */
        BigDecimal deposits,
        /** Money out to outside. */
        BigDecimal withdrawals,
        /** Moved in from your other Robinhood accounts. */
        BigDecimal transfersIn,
        /** Moved out to your other Robinhood accounts. */
        BigDecimal transfersOut,
        /** Broker realized P&amp;L for the window; null when the broker figure is not available. */
        BigDecimal realized,
        /** Interest recorded for the window. */
        BigDecimal income,
        /** Closing minus opening minus net money moved in. */
        BigDecimal marketChange,
        /** Market change that is not realized P&amp;L or income (open positions and everything else). */
        BigDecimal unrealizedOther,
        /** Market change over opening value plus half the net money moved in, in percent. */
        BigDecimal returnPct,
        BigDecimal openingCash,
        BigDecimal closingCash,
        /** Cash used (negative) or released (positive) by trading, after money moved and income. */
        BigDecimal tradingCash) {}
