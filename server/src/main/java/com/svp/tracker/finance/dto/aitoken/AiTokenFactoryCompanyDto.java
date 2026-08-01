package com.svp.tracker.finance.dto.aitoken;

import java.util.List;

public record AiTokenFactoryCompanyDto(
        String name,
        String symbol,
        boolean publicTicker,
        boolean coveredOnWatchImage,
        String role,
        String economicsNote,
        Double price,
        Double changePercentDay,
        Double changePercentMonthToDate,
        Double changePercentYearToDate,
        Double percentOf52WeekRange,
        /** 0–100 heuristic pulse (momentum + 52w position). Null if no quote. */
        Integer pulseScore,
        String pulseLabel,
        String quoteUrl,
        String companyResearchUrl,
        List<String> flags) {}
