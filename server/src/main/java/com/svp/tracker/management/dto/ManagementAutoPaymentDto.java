package com.svp.tracker.management.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ManagementAutoPaymentDto(
        long id,
        String name,
        String payee,
        String category,
        String paymentMethod,
        String frequency,
        BigDecimal amount,
        String currency,
        LocalDate startedOn,
        LocalDate nextPaymentOn,
        Integer dayOfMonth,
        LocalDate endedOn,
        String status,
        String fundingAccount,
        String confirmationRef,
        String website,
        String notes,
        LocalDate nextDebitOn,
        Integer daysUntilDebit,
        String createdAt,
        String updatedAt) {}
