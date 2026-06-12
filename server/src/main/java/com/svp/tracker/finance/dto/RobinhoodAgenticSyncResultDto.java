package com.svp.tracker.finance.dto;

import java.time.Instant;

public record RobinhoodAgenticSyncResultDto(
        boolean ok,
        Instant syncedAt,
        String message,
        int positionCount,
        int accountsSynced) {}
