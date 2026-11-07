package com.svp.tracker.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ManagementMailAccountWriteRequest(
        @NotBlank String provider,
        @NotBlank @Size(max = 320) String emailAddress,
        @Size(max = 200) String appPassword,
        Boolean enabled) {}
