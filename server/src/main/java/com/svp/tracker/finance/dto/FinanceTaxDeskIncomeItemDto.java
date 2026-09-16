package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record FinanceTaxDeskIncomeItemDto(
        Long id,
        String kind,
        String payer,
        BigDecimal ytdAmount,
        BigDecimal annualProjected,
        BigDecimal withholdingYtd,
        BigDecimal withholdingAnnualProjected,
        String notes,
        int sortOrder) {}
