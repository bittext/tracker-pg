package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record FinanceLoanDto(
        long id,
        String institution,
        String loanNature,
        String loanNatureLabel,
        String natureOther,
        LocalDate dateAvailed,
        LocalDate dateToCommence,
        BigDecimal currentBalance,
        BigDecimal interestRate,
        BigDecimal paidSoFar,
        BigDecimal balanceToPay,
        String paymentFrequency,
        String paymentFrequencyLabel,
        String notes,
        int documentCount,
        Instant createdAt,
        Instant updatedAt) {}
