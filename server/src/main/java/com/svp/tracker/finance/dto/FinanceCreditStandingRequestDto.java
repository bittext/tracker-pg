package com.svp.tracker.finance.dto;

import java.time.LocalDate;

public record FinanceCreditStandingRequestDto(
        Integer score,
        String bureau,
        LocalDate reportedAsOf,
        String notes,
        LocalDate annualReportPulledAt) {}
