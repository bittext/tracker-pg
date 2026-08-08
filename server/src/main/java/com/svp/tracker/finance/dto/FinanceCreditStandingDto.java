package com.svp.tracker.finance.dto;

import java.time.Instant;
import java.time.LocalDate;

public record FinanceCreditStandingDto(
        Long id,
        Integer score,
        String bureau,
        LocalDate reportedAsOf,
        String notes,
        LocalDate annualReportPulledAt,
        int documentCount,
        Instant createdAt,
        Instant updatedAt) {}
