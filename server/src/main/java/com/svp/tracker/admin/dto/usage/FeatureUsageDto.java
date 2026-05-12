package com.svp.tracker.admin.dto.usage;

/**
 * Adoption metrics for a single feature/table in a time window.
 * {@code totalCount} counts rows created in [from, now]; {@code activeUsers} = distinct {@code owner_user_id} in that window.
 */
public record FeatureUsageDto(
        String feature,
        long totalCount,
        long activeUsers,
        long allTimeCount,
        /** ISO UTC instant of most recent row across all owners, or null when feature is unused. */
        String lastActivityAt) {}
