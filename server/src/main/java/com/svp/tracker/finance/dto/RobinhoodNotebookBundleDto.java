package com.svp.tracker.finance.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Self-contained export for Jupyter / pandas workflows: raw CSV-shaped rows plus the same FIFO performance
 * report the web UI uses.
 */
public record RobinhoodNotebookBundleDto(
        int year,
        String filterInstrument,
        Instant exportedAt,
        int transactionRowCount,
        boolean transactionsTruncated,
        List<Map<String, Object>> transactions,
        RobinhoodPerformanceReportDto performanceReport,
        List<RobinhoodClosedTradeDto> closedTrades,
        String usageNote) {}
