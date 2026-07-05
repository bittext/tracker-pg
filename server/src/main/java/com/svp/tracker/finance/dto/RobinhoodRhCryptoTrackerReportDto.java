package com.svp.tracker.finance.dto;

import java.util.List;

/** Crypto Tracker report for Reports → Robinhood → Crypto Tracker tab. */
public record RobinhoodRhCryptoTrackerReportDto(
        int year,
        List<Integer> months,
        String status,
        boolean sidecarConfigured,
        boolean cryptoConnected,
        boolean cryptoSyncAvailable,
        int snapshotCount,
        List<RobinhoodRhCryptoTrackerDayDto> days,
        List<String> notes) {}
