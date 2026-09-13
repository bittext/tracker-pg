package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

/** Compact close-of-day holding for the Daily Tracker account book. */
public record RobinhoodRhDailyTrackerHoldingBriefDto(
        String symbol,
        String positionType,
        BigDecimal quantity,
        BigDecimal marketValue,
        BigDecimal quantityChange,
        BigDecimal marketValueChange,
        boolean exited) {}
