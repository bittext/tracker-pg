package com.svp.tracker.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MePasswordChangeRequestDto(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8, max = 200, message = "New password must be at least 8 characters") String newPassword) {}
