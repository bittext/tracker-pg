package com.svp.tracker.finance.dto;

import java.time.Instant;
import java.util.List;

public record RobinhoodAgenticStatusDto(
        boolean featureEnabled,
        boolean serviceConfigured,
        boolean connected,
        String agenticAccountNumberMasked,
        String agenticNickname,
        Instant connectedAt,
        Instant lastSyncAt,
        String lastSyncStatus,
        String lastSyncMessage,
        int positionCount) {}
