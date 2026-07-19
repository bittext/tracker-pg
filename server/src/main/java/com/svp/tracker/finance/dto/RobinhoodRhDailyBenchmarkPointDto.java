package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** S&P 500 close aligned to one Daily Tracker snapshot date. */
public record RobinhoodRhDailyBenchmarkPointDto(
        LocalDate snapshotDate, LocalDate marketDate, BigDecimal close) {}
