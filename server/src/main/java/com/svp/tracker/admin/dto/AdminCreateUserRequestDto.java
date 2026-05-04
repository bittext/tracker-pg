package com.svp.tracker.admin.dto;

import com.svp.tracker.auth.domain.AppUserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminCreateUserRequestDto(
        @NotBlank @Size(max = 120) String username,
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 8, max = 200) String password,
        @NotNull AppUserRole role,
        boolean mfaEnabled,
        boolean active) {}
