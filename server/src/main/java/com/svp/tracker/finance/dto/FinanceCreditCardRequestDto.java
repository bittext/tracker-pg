package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinanceCreditCardRequestDto(
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
        String notes) {}
