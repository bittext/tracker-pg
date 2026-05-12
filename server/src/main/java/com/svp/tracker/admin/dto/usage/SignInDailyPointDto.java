package com.svp.tracker.admin.dto.usage;

/** One day of sign-in events grouped by {@code event_type}. */
public record SignInDailyPointDto(
        /** ISO local date in UTC (e.g. {@code 2026-05-11}). */
        String day,
        /** {@code LOGIN_SUCCESS}, {@code LOGIN_FAILED}, {@code MFA_REQUIRED}, {@code MFA_FAILED}, {@code LOGOUT}. */
        String eventType,
        long count) {}
