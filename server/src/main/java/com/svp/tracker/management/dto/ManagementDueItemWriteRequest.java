package com.svp.tracker.management.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ManagementDueItemWriteRequest(
        @NotBlank @Size(max = 16) String side,
        @NotBlank @Size(max = 200) String counterparty,
        boolean recurring,
        @Min(1) @Max(31) Integer dayOfMonth,
        LocalDate oneOffDate,
        BigDecimal amountOverride,
        @Size(max = 4000) String notes,
        @Min(1970) @Max(9999) Integer startYear,
        @Min(1) @Max(12) Integer startMonth) {}
