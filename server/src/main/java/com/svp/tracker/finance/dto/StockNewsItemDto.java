package com.svp.tracker.finance.dto;

/** One normalized stock-news item. */
public record StockNewsItemDto(
        String title,
        String source,
        String publishedAt,
        String url,
        String summary) {}
