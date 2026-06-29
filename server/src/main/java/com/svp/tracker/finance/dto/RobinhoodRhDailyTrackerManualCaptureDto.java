package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RobinhoodRhDailyTrackerManualCaptureDto(
        Instant capturedAt,
        BigDecimal combinedTotal,
        List<RobinhoodRhDailyTrackerManualCaptureAccountDto> accounts) {}
