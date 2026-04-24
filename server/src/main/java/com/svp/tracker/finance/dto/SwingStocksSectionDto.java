package com.svp.tracker.finance.dto;

import java.util.List;

/** “Swing Stocks” block for the Finance Crawler: Yahoo screener movers, KPIs, headlines, and sector context. */
public record SwingStocksSectionDto(
        String source, String note, String fetchedAt, int swingRowsRequested, List<SwingStockDetailDto> rows) {}
