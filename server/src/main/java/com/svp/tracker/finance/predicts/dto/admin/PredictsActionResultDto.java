package com.svp.tracker.finance.predicts.dto.admin;

import java.time.Instant;

/**
 * Generic response from admin-trigger endpoints. {@code message} is a one-line human summary safe to
 * surface in a snackbar; {@code count} is the affected row count (mentions ingested, baselines updated,
 * mentions purged, tickers seeded — meaning depends on the action).
 */
public record PredictsActionResultDto(String action, boolean ok, String message, long count, Instant ranAt) {

    public static PredictsActionResultDto ok(String action, String message, long count) {
        return new PredictsActionResultDto(action, true, message, count, Instant.now());
    }

    public static PredictsActionResultDto failed(String action, String message) {
        return new PredictsActionResultDto(action, false, message, 0L, Instant.now());
    }
}
