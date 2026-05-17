package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

/** Monthly rollup of realized P&amp;L for profit-over-time charts. */
public record RobinhoodMonthlyPnLPointDto(String yearMonth, String monthLabel, BigDecimal realizedPnL) {}
