package com.svp.tracker.admin.dto;

/** One row for Admin → My profile: members who have saved a member profile (public id minted). */
public record AdminMemberProfileListItemDto(
        long userId,
        String username,
        long memberPublicId,
        String displayName,
        boolean onboardingCompleted) {}
