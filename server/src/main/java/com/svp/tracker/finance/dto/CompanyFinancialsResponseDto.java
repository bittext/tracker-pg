package com.svp.tracker.finance.dto;

import java.util.List;

public record CompanyFinancialsResponseDto(
        String symbol,
        String companyName,
        /** Oldest -> newest, up to 12 quarters (~3 years). */
        List<CompanyFinancialsQuarterDto> quarters,
        CompanyFinancialsTrendDto trend,
        String source,
        String cachedAt) {}
