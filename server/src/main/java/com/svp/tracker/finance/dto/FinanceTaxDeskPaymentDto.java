package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinanceTaxDeskPaymentDto(
        Long id,
        LocalDate paidOn,
        BigDecimal amount,
        String method,
        String source,
        String sourceRef,
        String notes) {}
