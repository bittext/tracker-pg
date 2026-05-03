package com.svp.tracker.management.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ManagementWriteupWriteRequest(
        @NotNull @Min(1970) @Max(9999) Integer year,
        @NotBlank @Size(max = 2000) String topic,
        @Size(max = 2000) String highlight,
        @Size(max = 500_000) String body) {}
