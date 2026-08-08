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
        /**
         * True when this contract appears with quantity &gt; 0 on the newest Daily Tracker snapshot for the
         * account. Legacy lots keyed by an old average price often look "open" on their last seen day even
         * after they left the book — use this flag, not {@code latestQuantity} alone.
         */
        boolean currentlyOpen,
        /** Set when {@code currentlyOpen} is false — last snapshot date the contract was held. */
        LocalDate closedDate,
        BigDecimal latestQuantity,
        BigDecimal latestMarketValue,
        BigDecimal latestCostBasis,
        BigDecimal latestUnrealizedPnL,
        /** Unrealized P&amp;L as percent of cost basis; null when cost is zero/missing. */
        BigDecimal latestUnrealizedPnLPercent,
        BigDecimal highQuantity,
        LocalDate highDate) {}
