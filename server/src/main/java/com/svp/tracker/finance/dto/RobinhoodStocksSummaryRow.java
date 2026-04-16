package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of {@link RobinhoodStocksSummaryDto}: activity in a calendar (financial) year for an instrument + contract
 * line, with buy vs sell legs and date range from first open to last close in that year.
 */
public record RobinhoodStocksSummaryRow(
        String instrument,
        String contract,
        int financialYear,
        BigDecimal totalBuyQuantity,
        BigDecimal totalSellQuantity,
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
