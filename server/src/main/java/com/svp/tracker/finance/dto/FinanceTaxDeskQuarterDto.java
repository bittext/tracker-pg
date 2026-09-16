package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinanceTaxDeskQuarterDto(
        int quarter,
        LocalDate dueDate,
        BigDecimal requiredInstallment,
        BigDecimal requiredToDate,
        BigDecimal withholdingCredit,
        BigDecimal estimateCredit,
        BigDecimal paidToDate,
        BigDecimal shortfall,
        BigDecimal suggestedPayment,
        String status) {}
