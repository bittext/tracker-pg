package com.svp.tracker.management.domain;

import java.util.Locale;
import org.springframework.lang.Nullable;

/** Life (Management) vs Work desks keep documents, calendar, notes, and similar records separate. */
public enum ManagementDesk {
    LIFE,
    WORK;

    public static ManagementDesk fromParam(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return LIFE;
        }
        try {
            return ManagementDesk.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return LIFE;
        }
    }
}
