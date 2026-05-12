package com.svp.tracker.admin.dto.usage;

import java.util.Map;

/**
 * One user's adoption snapshot for the per-member table. {@code perFeature} keys match the feature labels used by
 * {@link FeatureUsageDto#feature()} (e.g. "Tasks", "Month notes", "Banking").
 */
public record MemberUsageDto(
        long userId,
        String username,
        String displayName,
        String role,
        boolean active,
        String createdAt,
        String lastLoginAt,
        long signInsSuccess30d,
        long totalItems,
        Map<String, Long> perFeature) {}
