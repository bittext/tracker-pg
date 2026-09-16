package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record FinanceTaxDeskSettingsDto(
        Long id,
        int taxYear,
        String filingStatus,
        String residentState,
        BigDecimal shortTermLossCarryover,
        BigDecimal longTermLossCarryover,
        BigDecimal priorYearAgi,
        BigDecimal priorYearTax,
        BigDecimal childTaxCredit,
        BigDecimal targetRefund,
        String notes) {}
