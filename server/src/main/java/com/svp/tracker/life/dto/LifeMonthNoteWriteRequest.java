package com.svp.tracker.life.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LifeMonthNoteWriteRequest(
        @NotNull @Min(1970) @Max(9999) Integer year,
        @NotNull @Min(1) @Max(12) Integer month,
        @NotBlank @Size(max = 2000) String subject,
        @Size(max = 100_000) String body) {}
