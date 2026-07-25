package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One daily point on a Then & now overlay series. */
public record InvestmentThenNowOverlayPointDto(
        LocalDate date,
        /** Close / as-of close × 100 (100 = break-even at as-of). */
        BigDecimal valuePct,
        /** shares × close. */
        BigDecimal valueUsd,
        BigDecimal close) {}
