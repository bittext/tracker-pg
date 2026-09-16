package com.svp.tracker.finance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FinanceTaxDeskSettingsWriteDto(
        String filingStatus,
        String residentState,
        BigDecimal shortTermLossCarryover,
        BigDecimal longTermLossCarryover,
        BigDecimal priorYearAgi,
        BigDecimal priorYearTax,
        BigDecimal childTaxCredit,
        BigDecimal targetRefund,
        String notes) {}
