package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record RobinhoodRhDailyTrackerAccountCellDto(
        long snapshotId,
        String accountSuffix,
        BigDecimal totalAccountValue,
        /** Raw total minus same account on the previous scheduled snapshot day. */
        BigDecimal totalChangeFromPrevious,
        BigDecimal periodAdded,
        BigDecimal periodRemoved,
        BigDecimal periodValueChange,
        boolean hasFlowActivity,
        int tradeCount,
        /** Stock/option quantity changes vs the immediately prior pull (false for ••••4123). */
        boolean positionsChangedFromPrior) {}
