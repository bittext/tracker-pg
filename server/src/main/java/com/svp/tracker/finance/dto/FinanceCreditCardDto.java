package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record FinanceCreditCardDto(
        long id,
        String institution,
        String cardName,
        String lastFour,
        BigDecimal creditLimit,
        BigDecimal currentBalance,
        BigDecimal apr,
        BigDecimal statementBalance,
        LocalDate statementDate,
        LocalDate paymentDueDate,
        Long bankingInstitutionId,
        String bankingInstitutionName,
        BigDecimal utilizationPct,
        BigDecimal availableCredit,
        String healthLabel,
        String notes,
        int documentCount,
        Instant createdAt,
        Instant updatedAt) {}
