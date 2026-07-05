package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record RhDailyTrackerAccountAlertItemDto(
        String accountSuffix,
        boolean enabled,
        boolean valueDollarsEnabled,
        BigDecimal minValueChangeDollars,
        boolean valuePercentEnabled,
        BigDecimal minValueChangePercent,
        boolean positionChangeEnabled,
        Integer cooldownMinutes) {}
