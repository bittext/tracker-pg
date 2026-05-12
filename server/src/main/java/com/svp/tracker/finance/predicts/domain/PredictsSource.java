package com.svp.tracker.finance.predicts.domain;

import java.util.Locale;

/**
 * Canonical source identifiers persisted in {@code finance_predicts_mentions.source},
 * {@code finance_predicts_buckets.source}, etc. Stored as the lower-case enum name (e.g. "stocktwits"),
 * which keeps the wire format identical to the dashboard chips and the source-health primary key.
 */
public enum PredictsSource {
    STOCKTWITS,
    REDDIT,
    X;

    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static PredictsSource fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("source is required");
        }
        try {
            return PredictsSource.valueOf(wire.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown predicts source: " + wire);
        }
    }
}
