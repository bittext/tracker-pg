package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of {@link RobinhoodStocksSummaryDto}: activity in a calendar (financial) year for an instrument + contract
 * line. Option legs are split by trans code ({@code BTO}/{@code STC}/{@code STO}/{@code BTC}); stock legs use
 * {@code BUY}/{@code SELL}.
 */
public record RobinhoodStocksSummaryRow(
        String instrument,
        String contract,
        int financialYear,
        /** {@code BTO + BTC + stock buy} quantity (legacy aggregate). */
        BigDecimal totalBuyQuantity,
        /** {@code STC + STO + stock sell} quantity (legacy aggregate). */
        BigDecimal totalSellQuantity,
        /** Buy to open (long option opens). */
        BigDecimal btoQuantity,
        /** Sell to close (long option closes). */
        BigDecimal stcQuantity,
        /** Sell to open (short option opens). */
        BigDecimal stoQuantity,
        /** Buy to close (short option closes). */
        BigDecimal btcQuantity,
        /** Stock {@code BUY} quantity. */
        BigDecimal stockBuyQuantity,
        /** Stock {@code SELL} quantity. */
        BigDecimal stockSellQuantity,
        /** Sum of {@code AMOUNT} on buy-side legs (typically negative cash out). */
        BigDecimal totalBuyAmount,
        /** Sum of {@code AMOUNT} on sell-side legs (typically positive cash in). */
        BigDecimal totalSellAmount,
        /** {@code totalBuyAmount + totalSellAmount} for the year bucket. */
        BigDecimal netAmount,
        LocalDate firstBuyDate,
        LocalDate lastBuyDate,
        LocalDate firstSellDate,
        LocalDate lastSellDate,
        int buyLegCount,
        int sellLegCount) {}
