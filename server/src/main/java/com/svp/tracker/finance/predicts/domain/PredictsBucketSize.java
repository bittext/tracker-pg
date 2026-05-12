package com.svp.tracker.finance.predicts.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Canonical aggregation widths for {@link PredictsBucket}. Stored as the wire token
 * ("5m", "15m", "1h", "1d") so the URL query parameter, DB row, and UI chip stay aligned.
 */
public enum PredictsBucketSize {
    FIVE_MIN("5m", Duration.ofMinutes(5)),
    FIFTEEN_MIN("15m", Duration.ofMinutes(15)),
    ONE_HOUR("1h", Duration.ofHours(1)),
    ONE_DAY("1d", Duration.ofDays(1));

    private final String wire;
    private final Duration duration;

    PredictsBucketSize(String wire, Duration duration) {
        this.wire = wire;
        this.duration = duration;
    }

    public String wire() {
        return wire;
    }

    public Duration duration() {
        return duration;
    }

    public static PredictsBucketSize fromWire(String wire) {
        if (wire == null) {
            return ONE_HOUR;
        }
        String t = wire.trim().toLowerCase(Locale.ROOT);
        for (PredictsBucketSize s : values()) {
            if (s.wire.equals(t)) {
                return s;
            }
        }
        throw new IllegalArgumentException("unknown bucket size: " + wire);
    }

    /**
     * Truncates an instant to the start of its enclosing bucket. {@link ChronoUnit} doesn't support
     * arbitrary minute widths (5, 15), so we floor the epoch seconds manually for sub-hour buckets.
     */
    public Instant truncate(Instant when) {
        long sec = when.getEpochSecond();
        long width = duration.getSeconds();
        if (this == ONE_DAY) {
            return when.atOffset(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        }
        long floored = sec - (sec % width);
        return Instant.ofEpochSecond(floored);
    }
}
