package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

/**
 * Snapshot holding plus hour-over-hour deltas vs the prior capture for the same account.
 * Change fields are null when there is no prior snapshot or the rounded delta is zero.
 */
public record RobinhoodRhDailySnapshotHoldingDto(
        RobinhoodRhHoldingDto holding,
        BigDecimal quantityChange,
        BigDecimal currentUnitPriceChange,
        BigDecimal marketValueChange,
        boolean exited) {}
