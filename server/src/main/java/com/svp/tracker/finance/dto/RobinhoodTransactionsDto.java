package com.svp.tracker.finance.dto;

import java.util.List;
import java.util.Map;

/** Rows from {@code tracker.finance.robinhood-table} with optional period filter. */
public record RobinhoodTransactionsDto(
        List<Map<String, Object>> rows,
        int returned,
        String tableQueried,
        int maxRowsCap,
        Integer filterYear,
        Integer filterMonth,
        String filterLabel) {}
