package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Lightweight Daily Tracker money fields for Individual margin backfill. */
public record RhMarginSnapshotMoneyRow(
        Instant snapshotAt,
        LocalDate snapshotDate,
        String captureKind,
        BigDecimal cashBalance,
        BigDecimal equityMarketValue,
        BigDecimal totalAccountValue) {}
