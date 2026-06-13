package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record FinanceInvestmentDto(
        long id,
        String institution,
        String investmentType,
        String investmentTypeLabel,
        String typeOther,
        String symbol,
        String name,
        LocalDate dateAcquired,
        BigDecimal quantity,
        BigDecimal costBasis,
        BigDecimal currentValue,
        BigDecimal gainLoss,
        String notes,
        int documentCount,
        Instant createdAt,
        Instant updatedAt) {}
