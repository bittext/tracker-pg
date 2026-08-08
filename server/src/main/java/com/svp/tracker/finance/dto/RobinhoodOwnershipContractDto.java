package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One option contract identity discovered in Daily Tracker holdings (for the Ownership history picker). */
public record RobinhoodOwnershipContractDto(
        String contractKey,
        String label,
        String chainSymbol,
        String optionType,
        BigDecimal strikePrice,
        LocalDate expirationDate,
        /** True when strike/expiry were missing in historical JSON and identity is inferred. */
        boolean legacyIdentity,
        LocalDate firstDate,
        LocalDate lastDate,
        BigDecimal latestQuantity,
        BigDecimal latestMarketValue,
        BigDecimal latestCostBasis,
        BigDecimal latestUnrealizedPnL,
        /** Unrealized P&amp;L as percent of cost basis; null when cost is zero/missing. */
        BigDecimal latestUnrealizedPnLPercent,
        BigDecimal highQuantity,
        LocalDate highDate) {}
