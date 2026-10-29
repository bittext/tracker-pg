package com.svp.tracker.fitness.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record AppleHealthWeightDto(
        Long id, LocalDate loggedOn, Instant measuredAt, BigDecimal weightLb, String sourceName) {}
