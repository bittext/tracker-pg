package com.svp.tracker.admin.dto.usage;

/**
 * Top-line counts for the Admin → Usage tab. All counts are global (across all owners).
 * "Active" = at least one LOGIN_SUCCESS in the window.
 */
public record UsageSummaryDto(
        long totalUsers,
        long activeUsers,
        long adminUsers,
        long memberProfilesCount,
        long activeUsers7d,
        long activeUsers30d,
        long signInsSuccess30d,
        long signInsFailed30d,
        long itemsCreated7d,
        long itemsCreated30d,
        /** UTC ISO instant; null when no rows exist anywhere. */
        String lastActivityAt) {}
