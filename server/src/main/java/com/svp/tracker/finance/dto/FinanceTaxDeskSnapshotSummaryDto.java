package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinanceTaxDeskSnapshotSummaryDto(
        LocalDate asOf,
        String riskLevel,
        BigDecimal estimatedTax,
        BigDecimal filingBalance,
        BigDecimal penaltyExposure,
        BigDecimal realizedYtd) {}
