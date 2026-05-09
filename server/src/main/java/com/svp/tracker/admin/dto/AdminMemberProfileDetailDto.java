package com.svp.tracker.admin.dto;

import com.svp.tracker.member.dto.MeMemberProfileResponseDto;

/** Full member profile for admin read-only viewing. */
public record AdminMemberProfileDetailDto(
        long userId,
        String username,
        String role,
        boolean onboardingCompleted,
        MeMemberProfileResponseDto profile) {}
