package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record FinanceCreditCardStatementDto(
        long id,
        long creditCardId,
        LocalDate statementDate,
        BigDecimal statementBalance,
        BigDecimal minimumPayment,
        LocalDate paymentDueDate,
        String notes,
        Instant createdAt,
        Instant updatedAt) {}
