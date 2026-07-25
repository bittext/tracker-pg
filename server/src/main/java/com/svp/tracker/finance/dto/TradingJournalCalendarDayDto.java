package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Month heatmap cell: Δ vs prior 9 PM CT scheduled close. */
public record TradingJournalCalendarDayDto(
        LocalDate snapshotDate,
        BigDecimal changeFromPrevious,
        boolean hasPreviousScheduledSnapshot) {}
