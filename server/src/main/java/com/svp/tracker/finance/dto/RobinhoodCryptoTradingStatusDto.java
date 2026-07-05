package com.svp.tracker.finance.dto;

import java.time.Instant;

public record RobinhoodCryptoTradingStatusDto(
        boolean featureEnabled,
        boolean sidecarConfigured,
        boolean connected,
        String accountNumberMasked,
        Instant connectedAt,
        Instant lastSyncAt,
        String lastSyncStatus,
        String lastSyncMessage) {}
