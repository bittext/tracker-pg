package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record RobinhoodRhDailyTrackerManualCaptureAccountDto(
        long snapshotId, String accountSuffix, String label, BigDecimal totalAccountValue) {}
