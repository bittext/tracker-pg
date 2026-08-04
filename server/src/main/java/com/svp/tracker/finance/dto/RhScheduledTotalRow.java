package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Lightweight scheduled-close totals (no holdings/trades JSON) for lookback / calendar. */
public record RhScheduledTotalRow(LocalDate snapshotDate, String accountSuffix, BigDecimal totalAccountValue) {}
