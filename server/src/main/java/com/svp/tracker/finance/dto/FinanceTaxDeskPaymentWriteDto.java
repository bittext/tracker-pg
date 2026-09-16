package com.svp.tracker.finance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FinanceTaxDeskPaymentWriteDto(
        LocalDate paidOn, BigDecimal amount, String method, String source, String notes) {}
