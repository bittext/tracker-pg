package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record FinanceTaxDeskFilingRefDto(
        long id,
        int taxYear,
        String originalFilename,
        String filingStatus,
        String confidenceLabel,
        BigDecimal wages,
        BigDecimal totalTax,
        BigDecimal withholding,
        BigDecimal estimatedPayments,
        BigDecimal refund,
        BigDecimal amountOwed,
        boolean parserUnreliable,
        String note) {}
