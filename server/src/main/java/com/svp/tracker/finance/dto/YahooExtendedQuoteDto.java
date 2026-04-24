package com.svp.tracker.finance.dto;

/**
 * A richer Yahoo v7/quote row for UI narratives (KPIs). Optional fields are often null on some tickers. Not
 * investment advice.
 */
public record YahooExtendedQuoteDto(
        String symbol,
        String shortName,
        String longName,
        Double regularMarketPrice,
        Double regularMarketChangePercent,
        Long regularMarketVolume,
        Long averageDailyVolume3Month,
        Double marketCap,
        Double fiftyTwoWeekHigh,
        Double fiftyTwoWeekLow,
        Double trailingPE,
        String sector,
        String industry) {}
