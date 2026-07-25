package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Detail answer for “$X invested on date in SYMBOL — worth now?” */
public record InvestmentThenNowResultDto(
        Long id,
        String symbol,
        String companyName,
        BigDecimal investedAmount,
        LocalDate asOfDate,
        BigDecimal priceAsOfDate,
        LocalDate priceAsOfSession,
        BigDecimal shares,
        BigDecimal priceNow,
        LocalDate priceNowSession,
        BigDecimal worthNow,
        BigDecimal gainAmount,
        BigDecimal gainPercent,
        String detailAnswer,
        String priceSource,
        Instant computedAt,
        Instant createdAt,
        Instant updatedAt,
        boolean saved) {}
