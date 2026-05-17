package com.svp.tracker.finance.dto;

import java.util.List;

/** Performance analytics from imported Robinhood CSV rows (FIFO realized P&amp;L on trade legs). */
public record RobinhoodPerformanceReportDto(
        int financialYear,
        String filterInstrument,
        String tableQueried,
        int rowsAnalyzed,
        boolean truncated,
        String note,
        RobinhoodPerformanceSummaryDto summary,
        List<RobinhoodDailyPnLPointDto> dailyPnL,
        List<RobinhoodMonthlyPnLPointDto> monthlyPnL,
        List<RobinhoodEquityCurvePointDto> equityCurve,
        List<RobinhoodClosedTradeDto> closedTrades,
        RobinhoodUnrealizedSectionDto unrealized,
        RobinhoodPerformanceInsightsDto insights,
        RobinhoodPerformanceTaxDto tax) {}
