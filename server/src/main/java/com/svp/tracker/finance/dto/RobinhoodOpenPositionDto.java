package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One open FIFO lot still held as of the report's as-of date. */
public record RobinhoodOpenPositionDto(
        String instrument,
        String contract,
        String strategy,
        LocalDate openedDate,
        int holdDaysAsOf,
        BigDecimal quantity,
        BigDecimal costBasis,
        /** Last quote when available (stocks only). */
        BigDecimal marketPrice,
        BigDecimal marketValue,
        BigDecimal unrealizedPnL,
        boolean quoteAvailable) {}
