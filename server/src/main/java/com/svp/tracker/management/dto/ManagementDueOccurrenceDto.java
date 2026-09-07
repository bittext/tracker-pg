package com.svp.tracker.management.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ManagementDueOccurrenceDto(
        long itemId,
        Long occurrenceId,
        String side,
        String counterparty,
        boolean recurring,
        Integer dayOfMonth,
        LocalDate oneOffDate,
        LocalDate occurrenceDate,
        BigDecimal amountOverride,
        BigDecimal estimatedAmount,
        BigDecimal displayAmount,
        String amountSource,
        String notes,
        boolean settled,
        BigDecimal settledAmount) {}
