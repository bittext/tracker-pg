package com.svp.tracker.finance.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class FinanceInsuranceRenewal {

    private FinanceInsuranceRenewal() {}

    public static Long daysUntilRenewal(LocalDate coverageEndDate) {
        if (coverageEndDate == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(), coverageEndDate);
    }

    /** {@code EXPIRED}, {@code DUE_SOON}, {@code OK}, or {@code NO_DATE}. */
    public static String renewalStatus(LocalDate coverageEndDate, int reminderDays) {
        Long days = daysUntilRenewal(coverageEndDate);
        if (days == null) {
            return "NO_DATE";
        }
        if (days < 0) {
            return "EXPIRED";
        }
        if (days <= Math.max(reminderDays, 0)) {
            return "DUE_SOON";
        }
        return "OK";
    }

    public static String renewalStatusLabel(String status) {
        return switch (status) {
            case "EXPIRED" -> "Expired";
            case "DUE_SOON" -> "Renewal due soon";
            case "OK" -> "Active";
            case "NO_DATE" -> "No end date";
            default -> status;
        };
    }
}
