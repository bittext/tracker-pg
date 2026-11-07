package com.svp.tracker.management.dto;

import java.time.Instant;

public record ManagementMailAccountDto(
        long id,
        String provider,
        String emailAddress,
        boolean enabled,
        boolean secretSet,
        Instant updatedAt) {}
