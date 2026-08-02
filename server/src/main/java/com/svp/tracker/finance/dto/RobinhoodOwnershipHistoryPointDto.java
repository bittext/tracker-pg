package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One scheduled (or chosen) Daily Tracker day for a symbol on one account. */
public record RobinhoodOwnershipHistoryPointDto(
        LocalDate snapshotDate,
        Instant snapshotAt,
        String captureKind,
        long snapshotId,
        BigDecimal quantity,
        BigDecimal marketValue,
        BigDecimal averageBuyPrice,
        BigDecimal costBasis,
        BigDecimal unrealizedPnL,
        BigDecimal currentUnitPrice,
        BigDecimal cashBalance,
        BigDecimal equityMarketValue,
        BigDecimal totalAccountValue,
        /** max(0, −cash) when the account is on margin. */
        BigDecimal marginLoan,
        /** qty × (1 − marginLoan/equityMV), when equityMV &gt; 0. */
        BigDecimal ownSharesEstimate,
        /** qty × (marginLoan/equityMV), when equityMV &gt; 0. */
        BigDecimal marginSharesEstimate) {}
