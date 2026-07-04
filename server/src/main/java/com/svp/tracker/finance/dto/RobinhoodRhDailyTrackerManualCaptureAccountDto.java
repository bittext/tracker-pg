package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record RobinhoodRhDailyTrackerManualCaptureAccountDto(
        long snapshotId,
        String accountSuffix,
        String label,
        BigDecimal totalAccountValue,
        /** Stock/option quantity changes vs the immediately prior pull (false for ••••4123). */
        boolean positionsChangedFromPrior) {}
