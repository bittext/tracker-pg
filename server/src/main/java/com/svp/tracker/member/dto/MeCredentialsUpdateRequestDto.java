package com.svp.tracker.member.dto;

import jakarta.validation.constraints.NotBlank;

public record MeCredentialsUpdateRequestDto(
        @NotBlank String currentPassword, String newUsername, String newPassword) {}
