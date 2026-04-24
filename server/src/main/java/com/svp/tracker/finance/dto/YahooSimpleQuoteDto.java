package com.svp.tracker.finance.dto;

/** One row from Yahoo v7 /v7/finance/quote. */
public record YahooSimpleQuoteDto(
        String symbol, String shortName, Double regularMarketPrice, Double regularMarketChangePercent) {}
