package com.svp.tracker.finance.dto;

import java.util.List;

/** Response for GET /api/finance/robinhood/news */
public record StockNewsDto(
        String requestedSymbol,
        String requestedCompanyName,
        int requestedLimit,
        int returned,
        String feed,
        String fetchedAt,
        String note,
        StockNewsAnalysisDto analysis,
        List<StockNewsItemDto> items) {}
