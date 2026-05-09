package com.svp.tracker.finance.dto;

import java.util.List;

/** GET /api/finance/robinhood/market-overview — Yahoo Finance quotes + daily history; not investment advice. */
public record MarketOverviewDto(
        String source,
        String fetchedAt,
        String note,
        List<String> warnings,
        MarketOverviewSummaryDto summary,
        List<MarketOverviewSectionDto> sections) {}
