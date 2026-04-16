package com.svp.tracker.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record MfaVerifyRequestDto(
        @NotBlank String challengeId,
        @NotBlank String otpCode,
        String locationFingerprintSource,
        String locationLabel) {}
