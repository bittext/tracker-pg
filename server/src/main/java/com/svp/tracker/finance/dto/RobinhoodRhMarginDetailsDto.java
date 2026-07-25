package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

/** Live margin / buying-power snapshot from Robinhood get_portfolio (+ get_accounts type). */
public record RobinhoodRhMarginDetailsDto(
        /** Brokerage trading type from get_accounts: margin, cash, … */
        String brokerageTradingType,
        String optionLevel,
        BigDecimal buyingPower,
        BigDecimal unleveragedBuyingPower,
        /** Extra spendable from margin: max(0, buyingPower − unleveraged). */
        BigDecimal marginExtraBuyingPower,
        /** Margin debit when cash balance is negative. */
        BigDecimal marginDebit,
        BigDecimal optionsValue,
        BigDecimal pendingDeposits,
        /** Equity market value as % of total account value. */
        Double equityInvestedPercent,
        /** Cash as % of total (can be negative when on margin). */
        Double cashPercent,
        boolean marginAccount,
        /** True when cash is negative or margin adds buying power beyond unleveraged. */
        boolean marginInUse) {}
