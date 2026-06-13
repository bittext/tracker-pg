package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record FinanceCreditCardSummaryDto(
        int cardCount,
        BigDecimal totalCreditLimit,
        BigDecimal totalCurrentBalance,
        BigDecimal overallUtilizationPct,
        String healthLabel) {}
