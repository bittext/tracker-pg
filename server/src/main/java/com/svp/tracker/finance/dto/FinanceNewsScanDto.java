package com.svp.tracker.finance.dto;

import java.util.List;

/** Markets header ticker-news scan. */
public record FinanceNewsScanDto(
        boolean enabled,
        List<String> tickers,
        String fetchedAt,
        String note,
        List<FinanceNewsScanHitDto> hits) {}
