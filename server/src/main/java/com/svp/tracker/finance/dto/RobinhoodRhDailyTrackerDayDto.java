package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record RobinhoodRhDailyTrackerDayDto(
        LocalDate snapshotDate,
        Instant snapshotAt,
        boolean hasScheduledSnapshot,
        BigDecimal combinedTotal,
        /** Raw combined total minus previous scheduled snapshot day (same 9 PM cadence). */
        BigDecimal combinedTotalChangeFromPrevious,
        boolean hasPreviousScheduledSnapshot,
        BigDecimal combinedPeriodAdded,
        BigDecimal combinedPeriodRemoved,
        BigDecimal combinedPeriodValueChange,
        List<RobinhoodRhDailyTrackerAccountCellDto> accounts,
        List<RobinhoodRhDailyTrackerManualCaptureDto> manualCaptures,
        List<RobinhoodRhDailyTradeDto> trades,
        String summaryNote) {}
