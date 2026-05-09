package com.svp.tracker.finance.dto;

/** One row in the Finance → Market overview (quote + period returns). Not investment advice. */
public record MarketOverviewInstrumentDto(
        String symbol,
        String displayName,
        Double regularMarketPrice,
        Double changePercentDay,
        Double changePercentMonthToDate,
        Double changePercentYearToDate,
        String quoteUrl) {}
