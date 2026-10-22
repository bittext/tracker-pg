package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One Central calendar day's Predict performance (settled closes + optional open mark). */
public record RhPredictDailyDto(
        LocalDate date,
        Instant capturedAt,
        int closes,
        BigDecimal invested,
        BigDecimal payback,
        BigDecimal returned,
        BigDecimal openValue,
        BigDecimal openCost,
        boolean official) {}
