package com.svp.tracker.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ManagementAccountWriteRequest(
        @NotBlank @Size(max = 256) String itemName,
        @Size(max = 256) String folder,
        @Size(max = 256) String username,
        @Size(max = 4096) String password,
        @Size(max = 4096) String authenticatorKey,
        @Size(max = 1024) String website,
        @Size(max = 16384) String notes) {}
