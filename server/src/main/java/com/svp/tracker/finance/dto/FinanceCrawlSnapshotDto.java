package com.svp.tracker.finance.dto;

import java.util.List;

/** Combined Finance “Crawler” tab payload: topic RSS + watchlist + major index marks + swing screeners. */
public record FinanceCrawlSnapshotDto(
        String fetchedAt,
        String sourceNote,
        int crawlHeadlineLimit,
        StockNewsDto generalNews,
        StockNewsDto financialNews,
        List<IndexSnapshotDto> majorIndexes,
        List<CrawlerWatchItemDto> watchlist,
        SwingStocksSectionDto swingStocks) {}
