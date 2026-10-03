package com.svp.tracker.management.service;

import com.svp.tracker.management.domain.ManagementAutoPaymentFrequency;
import com.svp.tracker.management.domain.ManagementAutoPaymentMethod;
import com.svp.tracker.management.domain.ManagementAutoPaymentStatus;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.springframework.lang.Nullable;

public final class ManagementAutoPaymentSupport {

    private ManagementAutoPaymentSupport() {}

    public static ManagementAutoPaymentStatus parseStatus(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return ManagementAutoPaymentStatus.ACTIVE;
        }
        try {
            return ManagementAutoPaymentStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ManagementAutoPaymentStatus.ACTIVE;
        }
    }

    public static ManagementAutoPaymentFrequency parseFrequency(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return ManagementAutoPaymentFrequency.MONTHLY;
        }
        try {
            return ManagementAutoPaymentFrequency.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ManagementAutoPaymentFrequency.OTHER;
        }
    }

    public static ManagementAutoPaymentMethod parseMethod(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return ManagementAutoPaymentMethod.ACH;
        }
        try {
            return ManagementAutoPaymentMethod.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ManagementAutoPaymentMethod.OTHER;
        }
    }

    @Nullable
    public static LocalDate plusFrequency(@Nullable LocalDate from, ManagementAutoPaymentFrequency frequency) {
        if (from == null || frequency == null || frequency == ManagementAutoPaymentFrequency.OTHER) {
            return null;
        }
        return switch (frequency) {
            case WEEKLY -> from.plusWeeks(1);
            case BIWEEKLY -> from.plusWeeks(2);
            case MONTHLY -> from.plusMonths(1);
            case ANNUAL -> from.plusYears(1);
            case OTHER -> null;
        };
    }

    @Nullable
    public static LocalDate dateOnDayOfMonth(@Nullable LocalDate monthAnchor, @Nullable Integer dayOfMonth) {
        if (monthAnchor == null || dayOfMonth == null || dayOfMonth < 1) {
            return null;
        }
        int day = Math.min(dayOfMonth, monthAnchor.lengthOfMonth());
        return monthAnchor.withDayOfMonth(day);
    }

    @Nullable
    public static LocalDate nextDebit(
            @Nullable LocalDate startedOn,
            @Nullable LocalDate nextPaymentOn,
            @Nullable Integer dayOfMonth,
            ManagementAutoPaymentFrequency frequency,
            ManagementAutoPaymentStatus status,
            LocalDate today) {
        if (status == ManagementAutoPaymentStatus.CANCELLED || status == ManagementAutoPaymentStatus.PAUSED) {
            return nextPaymentOn;
        }
        LocalDate base = nextPaymentOn;
        if (base == null) {
            LocalDate anchor = startedOn != null ? startedOn : today;
            base = dateOnDayOfMonth(anchor, dayOfMonth);
            if (base == null) {
                base = plusFrequency(startedOn, frequency);
            } else if (startedOn != null && base.isBefore(startedOn)) {
                base = dateOnDayOfMonth(anchor.plusMonths(1), dayOfMonth);
            }
        }
        if (base == null) {
            return null;
        }
        if (!base.isBefore(today)) {
            return base;
        }
        LocalDate cursor = base;
        for (int i = 0; i < 120; i++) {
            LocalDate next = plusFrequency(cursor, frequency);
            if (next == null) {
                if (dayOfMonth != null) {
                    next = dateOnDayOfMonth(cursor.plusMonths(1), dayOfMonth);
                }
                if (next == null) {
                    return cursor;
                }
            }
            cursor = next;
            if (!cursor.isBefore(today)) {
                return cursor;
            }
        }
        return cursor;
    }

    @Nullable
    public static Integer daysUntil(@Nullable LocalDate date, LocalDate today) {
        if (date == null || today == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(today, date);
    }
}
