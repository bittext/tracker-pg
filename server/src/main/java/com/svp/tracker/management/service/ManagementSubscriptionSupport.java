package com.svp.tracker.management.service;

import com.svp.tracker.management.domain.ManagementSubscriptionBillingCycle;
import com.svp.tracker.management.domain.ManagementSubscriptionStatus;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.springframework.lang.Nullable;

public final class ManagementSubscriptionSupport {

    public static final int REFUND_WINDOW_DAYS = 30;

    private ManagementSubscriptionSupport() {}

    public static ManagementSubscriptionStatus parseStatus(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return ManagementSubscriptionStatus.ACTIVE;
        }
        try {
            return ManagementSubscriptionStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ManagementSubscriptionStatus.ACTIVE;
        }
    }

    public static ManagementSubscriptionBillingCycle parseCycle(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return ManagementSubscriptionBillingCycle.ANNUAL;
        }
        try {
            return ManagementSubscriptionBillingCycle.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ManagementSubscriptionBillingCycle.OTHER;
        }
    }

    @Nullable
    public static LocalDate plusCycle(@Nullable LocalDate from, ManagementSubscriptionBillingCycle cycle) {
        if (from == null || cycle == null || cycle == ManagementSubscriptionBillingCycle.OTHER) {
            return null;
        }
        return switch (cycle) {
            case WEEKLY -> from.plusWeeks(1);
            case MONTHLY -> from.plusMonths(1);
            case ANNUAL -> from.plusYears(1);
            case OTHER -> null;
        };
    }

    @Nullable
    public static LocalDate nextRenewal(
            @Nullable LocalDate enrolledOn,
            @Nullable LocalDate renewsOn,
            ManagementSubscriptionBillingCycle cycle,
            ManagementSubscriptionStatus status,
            boolean autoRenew,
            LocalDate today) {
        if (status == ManagementSubscriptionStatus.CANCELLED || status == ManagementSubscriptionStatus.EXPIRED) {
            return renewsOn;
        }
        LocalDate base = renewsOn != null ? renewsOn : plusCycle(enrolledOn, cycle);
        if (base == null) {
            return null;
        }
        if (!autoRenew || !base.isBefore(today)) {
            return base;
        }
        LocalDate cursor = base;
        for (int i = 0; i < 120; i++) {
            LocalDate next = plusCycle(cursor, cycle);
            if (next == null) {
                return cursor;
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

    @Nullable
    public static LocalDate refundWindowEndsOn(@Nullable LocalDate enrolledOn) {
        return enrolledOn == null ? null : enrolledOn.plusDays(REFUND_WINDOW_DAYS);
    }

    public static boolean refundWindowOpen(
            @Nullable LocalDate enrolledOn, ManagementSubscriptionStatus status, LocalDate today) {
        if (enrolledOn == null || today == null) {
            return false;
        }
        if (status != ManagementSubscriptionStatus.ACTIVE && status != ManagementSubscriptionStatus.TRIAL) {
            return false;
        }
        LocalDate ends = refundWindowEndsOn(enrolledOn);
        return ends != null && !today.isAfter(ends);
    }
}
