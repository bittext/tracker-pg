package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Request to compute (and optionally save) a then/now investment replay. */
public record InvestmentThenNowRequestDto(
        String symbol,
        BigDecimal investedAmount,
        LocalDate asOfDate,
        /** When true, upsert the computed answer for the current user. */
        Boolean save) {}
