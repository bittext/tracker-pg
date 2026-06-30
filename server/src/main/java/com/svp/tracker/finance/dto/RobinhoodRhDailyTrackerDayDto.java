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
        BigDecimal combinedPeriodAdded,
        BigDecimal combinedPeriodRemoved,
        BigDecimal combinedPeriodValueChange,
        List<RobinhoodRhDailyTrackerAccountCellDto> accounts,
        List<RobinhoodRhDailyTrackerManualCaptureDto> manualCaptures,
        String summaryNote) {}
