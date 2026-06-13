package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinanceInsurancePolicyRequestDto(
        String carrier,
        String policyType,
        String typeOther,
        String policyNumber,
        String coverageDescription,
        BigDecimal premiumAmount,
        String premiumFrequency,
        LocalDate coverageStartDate,
        LocalDate coverageEndDate,
        Integer renewalReminderDays,
        String notes) {}
