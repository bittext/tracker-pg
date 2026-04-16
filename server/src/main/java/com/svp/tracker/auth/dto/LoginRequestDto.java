package com.svp.tracker.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequestDto(
        @NotBlank String username,
        @NotBlank String password,
        String locationFingerprintSource,
        String locationLabel) {}
