package com.svp.tracker.finance.dto;

import java.util.List;

/**
 * One watchlist name: deep-trusted headlines plus a Yahoo quote, index-relative session summary, and a short
 * text rollup from the headline heuristics.
 */
public record CrawlerWatchItemDto(
        String symbol,
        String companyLabel,
        String searchNote,
        StockNewsDto news,
        YahooSimpleQuoteDto quote,
        String vsMarketSummary,
        String analysisSummary,
        List<String> dataWarnings) {}
