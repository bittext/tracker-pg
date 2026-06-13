package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinanceCreditCardStatementRequestDto(
        LocalDate statementDate,
        BigDecimal statementBalance,
        BigDecimal minimumPayment,
        LocalDate paymentDueDate,
        String notes) {}
