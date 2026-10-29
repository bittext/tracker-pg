package com.svp.tracker.fitness.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record AppleHealthWorkoutDto(
        Long id,
        String activity,
        String sourceName,
        Instant startedAt,
        Instant endedAt,
        LocalDate day,
        int durationMinutes,
        BigDecimal distanceMiles,
        BigDecimal energyKcal) {}
