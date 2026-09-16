package com.svp.tracker.finance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FinanceTaxDeskIncomeItemWriteDto(
        String kind,
        String payer,
        BigDecimal ytdAmount,
        BigDecimal annualProjected,
        BigDecimal withholdingYtd,
        BigDecimal withholdingAnnualProjected,
        String notes,
        Integer sortOrder) {}
