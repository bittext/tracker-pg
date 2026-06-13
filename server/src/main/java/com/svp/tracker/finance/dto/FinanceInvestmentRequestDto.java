package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinanceInvestmentRequestDto(
        String institution,
        String investmentType,
        String typeOther,
        String symbol,
        String name,
        LocalDate dateAcquired,
        BigDecimal quantity,
        BigDecimal costBasis,
        BigDecimal currentValue,
        String notes) {}
