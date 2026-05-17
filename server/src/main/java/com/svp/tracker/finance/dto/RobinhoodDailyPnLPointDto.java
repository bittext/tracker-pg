package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Realized P&amp;L attributed to a calendar day (FIFO closes on sell activity dates). */
public record RobinhoodDailyPnLPointDto(LocalDate date, BigDecimal realizedPnL, int closedLots) {}
