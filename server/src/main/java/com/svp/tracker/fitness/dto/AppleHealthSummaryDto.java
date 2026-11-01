package com.svp.tracker.fitness.dto;

import java.time.Instant;

public record AppleHealthSummaryDto(
        String fileName,
        Instant importedAt,
        long workouts,
        long weights,
        boolean syncReady,
        String syncToken) {}
