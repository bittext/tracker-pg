package com.svp.tracker.finance.predicts.dto;

import java.time.Instant;

public record PredictsSourceHealthDto(
        String source,
        boolean enabled,
        Instant lastAttemptAt,
        Instant lastSuccessAt,
        Instant lastErrorAt,
        String lastErrorMessage,
        int consecutiveFailures,
        int mentionsIngested24h) {}
