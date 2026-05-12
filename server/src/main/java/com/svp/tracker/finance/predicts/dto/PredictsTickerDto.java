package com.svp.tracker.finance.predicts.dto;

import java.time.Instant;
import java.util.List;

/**
 * Tracked ticker as returned to clients. {@code sourcesEnabled} is the canonical comma-list converted
 * to a list of wire tokens (e.g. ["stocktwits"]); {@code autoSeeded=true} means the ticker was added
 * by the Robinhood-holdings seeder and won't count against the per-user quota.
 */
public record PredictsTickerDto(
        long id,
        String symbol,
        boolean autoSeeded,
        List<String> sourcesEnabled,
        String note,
        Instant createdAt,
        Instant updatedAt) {}
