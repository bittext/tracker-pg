package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinanceLoanRequestDto(
        String institution,
        String loanNature,
        String natureOther,
        LocalDate dateAvailed,
        LocalDate dateToCommence,
        BigDecimal currentBalance,
        BigDecimal interestRate,
        BigDecimal paidSoFar,
        BigDecimal balanceToPay,
        String paymentFrequency,
        String notes) {}
