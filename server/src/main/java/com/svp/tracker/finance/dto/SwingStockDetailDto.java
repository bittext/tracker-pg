package com.svp.tracker.finance.dto;

import java.util.List;

/**
 * One name pulled from day gainers / losers (largest |%| in the screener mix) plus news + KPIs + a written synthesis.
 */
public record SwingStockDetailDto(
        YahooExtendedQuoteDto quote,
        StockNewsDto news,
        String performanceReport,
        String kpiNarrative,
        List<SectorPeerMoveDto> sectorPeers,
        List<String> warnings,
        /** Short label for UI chips — heuristic only, not analyst consensus. */
        String nearTermOutlookTilt,
        /** Why the tilt was chosen; cites common empirical patterns without claiming a prediction. */
        String nearTermOutlookNarrative) {}
