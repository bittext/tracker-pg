package com.svp.tracker.finance.dto;

/** Latest today headline for one scanned ticker. */
public record FinanceNewsScanHitDto(
        String symbol,
        String title,
        String source,
        String url,
        String publishedAt,
        boolean trustedOutlet,
        int headlineCount) {}
