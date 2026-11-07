package com.svp.tracker.management.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public enum ManagementMailSlot {
    MORNING("Morning"),
    MIDDAY("Midday"),
    EVENING("Evening");

    public static final ZoneId ZONE = ZoneId.of("America/Chicago");

    private final String label;

    ManagementMailSlot(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Mail included in this digest: overnight into morning, then since 7 AM, then since 1 PM. */
    public ZonedDateTime windowStart(LocalDate digestOn) {
        return switch (this) {
            case MORNING -> ZonedDateTime.of(digestOn.minusDays(1), LocalTime.of(19, 0), ZONE);
            case MIDDAY -> ZonedDateTime.of(digestOn, LocalTime.of(7, 0), ZONE);
            case EVENING -> ZonedDateTime.of(digestOn, LocalTime.of(13, 0), ZONE);
        };
    }

    public static ManagementMailSlot fromParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return forNow(ZonedDateTime.now(ZONE));
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Choose morning, midday, or evening.");
        }
    }

    public static ManagementMailSlot forNow(ZonedDateTime now) {
        int hour = now.getHour();
        if (hour < 12) {
            return MORNING;
        }
        if (hour < 17) {
            return MIDDAY;
        }
        return EVENING;
    }
}
