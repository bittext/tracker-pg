package com.svp.tracker.management.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ManagementAutoPaymentWriteRequest(
        @NotBlank @Size(max = 256) String name,
        @Size(max = 256) String payee,
        @Size(max = 128) String category,
        @Size(max = 16) String paymentMethod,
        @Size(max = 16) String frequency,
        BigDecimal amount,
        @Size(max = 8) String currency,
        LocalDate startedOn,
        LocalDate nextPaymentOn,
        @Min(1) @Max(31) Integer dayOfMonth,
        LocalDate endedOn,
        @Size(max = 16) String status,
        @Size(max = 256) String fundingAccount,
        @Size(max = 256) String confirmationRef,
        @Size(max = 1024) String website,
        @Size(max = 16384) String notes) {}
