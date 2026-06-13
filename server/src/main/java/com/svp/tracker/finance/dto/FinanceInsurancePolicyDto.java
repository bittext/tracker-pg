package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record FinanceInsurancePolicyDto(
        long id,
        String carrier,
        String policyType,
        String policyTypeLabel,
        String typeOther,
        String policyNumber,
        String coverageDescription,
        BigDecimal premiumAmount,
        String premiumFrequency,
        String premiumFrequencyLabel,
        BigDecimal annualizedPremium,
        LocalDate coverageStartDate,
        LocalDate coverageEndDate,
        int renewalReminderDays,
        Long daysUntilRenewal,
        String renewalStatus,
        String renewalStatusLabel,
        String notes,
        int documentCount,
        Instant createdAt,
        Instant updatedAt) {}
