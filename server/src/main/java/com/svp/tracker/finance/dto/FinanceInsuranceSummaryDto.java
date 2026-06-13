package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record FinanceInsuranceSummaryDto(
        int policyCount,
        int dueSoonCount,
        int expiredCount,
        BigDecimal totalAnnualPremium) {}
