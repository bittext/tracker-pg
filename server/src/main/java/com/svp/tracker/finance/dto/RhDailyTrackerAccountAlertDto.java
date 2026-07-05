package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record RhDailyTrackerAccountAlertDto(
        String accountSuffix,
        String label,
        String accountKind,
        boolean enabled,
        boolean valueDollarsEnabled,
        BigDecimal minValueChangeDollars,
        boolean valuePercentEnabled,
        BigDecimal minValueChangePercent,
        boolean positionChangeEnabled,
        int cooldownMinutes) {}
