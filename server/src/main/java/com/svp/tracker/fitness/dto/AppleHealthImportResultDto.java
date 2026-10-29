package com.svp.tracker.fitness.dto;

import java.time.Instant;

public record AppleHealthImportResultDto(
        String fileName,
        Instant importedAt,
        int workoutsAdded,
        int weightsAdded,
        int workoutsSkipped,
        int weightsSkipped,
        boolean truncated) {}
