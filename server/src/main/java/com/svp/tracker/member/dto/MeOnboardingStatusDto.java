package com.svp.tracker.member.dto;

public record MeOnboardingStatusDto(
        boolean onboardingCompleted,
        boolean credentialsStepCompleted,
        boolean profileSubmitted,
        Long memberPublicId,
        String username) {}
