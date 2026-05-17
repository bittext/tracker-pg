package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Cumulative realized P&amp;L through each date (equity curve). */
public record RobinhoodEquityCurvePointDto(LocalDate date, BigDecimal cumulativePnL) {}
