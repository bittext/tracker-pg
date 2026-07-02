package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record RobinhoodRhDailyTrackerAccountCellDto(
        long snapshotId,
        String accountSuffix,
        BigDecimal totalAccountValue,
        BigDecimal periodAdded,
        BigDecimal periodRemoved,
        BigDecimal periodValueChange,
        boolean hasFlowActivity,
        int tradeCount) {}
