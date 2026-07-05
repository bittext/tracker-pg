package com.svp.tracker.finance.dto;

import java.util.List;

/**
 * Crypto Tracker report for Reports → Robinhood → Crypto Tracker tab.
 *
 * @param status {@link com.svp.tracker.finance.service.RobinhoodRhCryptoTrackerService#STATUS_WAITING_FOR_MCP}
 *     until Robinhood Agentic MCP exposes crypto position reads.
 */
public record RobinhoodRhCryptoTrackerReportDto(
        int year,
        List<Integer> months,
        String status,
        boolean agenticServiceConfigured,
        boolean agenticConnected,
        boolean cryptoSyncAvailable,
        int snapshotCount,
        List<RobinhoodRhCryptoTrackerDayDto> days,
        List<String> notes) {}
