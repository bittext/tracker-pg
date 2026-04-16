package com.svp.tracker.finance.dto;

import java.util.List;

/** Buy/sell summary by instrument and contract for a calendar year (used as financial year). */
public record RobinhoodStocksSummaryDto(
        List<RobinhoodStocksSummaryRow> rows,
        int financialYear,
        /** Optional instrument filter (same meaning as {@code /transactions?symbol=}). */
        String filterInstrument,
        String tableQueried,
        int maxRowsCap,
        /**
         * True when the underlying transaction query hit {@code maxRowsCap}; summary may omit later rows for that
         * year.
         */
        boolean truncated,
        String note) {}
