package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Last capture (manual or scheduled) before this calendar day starts. */
public record RobinhoodRhDailyTrackerPriorPullDto(
        LocalDate snapshotDate,
        Instant snapshotAt,
        String captureKind,
        BigDecimal combinedTotal,
        List<RobinhoodRhDailyTrackerPriorPullAccountDto> accounts) {}
