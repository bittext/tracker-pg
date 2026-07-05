package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

/** Spike alert fired for a specific snapshot/account pull (excludes test sends). */
public record RhDailyTrackerSnapshotAlertDto(
        boolean fired,
        String emailStatus,
        String triggerReasons,
        BigDecimal deltaDollars,
        BigDecimal deltaPercent) {

    public static RhDailyTrackerSnapshotAlertDto none() {
        return new RhDailyTrackerSnapshotAlertDto(false, null, null, null, null);
    }
}
