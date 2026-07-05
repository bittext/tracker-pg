package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RhDailyTrackerAlertEventDto(
        long id,
        String accountSuffix,
        Long snapshotId,
        Long priorSnapshotId,
        String triggerReasons,
        BigDecimal deltaDollars,
        BigDecimal deltaPercent,
        String emailStatus,
        String destinationMasked,
        String detail,
        Instant createdAt) {}
