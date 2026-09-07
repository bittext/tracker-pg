package com.svp.tracker.management.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ManagementDueSettleRequest(
        @NotNull @Min(1970) @Max(9999) Integer year,
        @NotNull @Min(1) @Max(12) Integer month,
        boolean settled,
        BigDecimal settledAmount) {}
