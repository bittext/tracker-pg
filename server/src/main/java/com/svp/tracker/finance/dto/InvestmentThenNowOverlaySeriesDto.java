package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Historical overlay for one saved Then & now scenario. */
public record InvestmentThenNowOverlaySeriesDto(
        long id,
        String symbol,
        String companyName,
        BigDecimal investedAmount,
        LocalDate asOfDate,
        LocalDate priceAsOfSession,
        BigDecimal shares,
        String colorHint,
        List<InvestmentThenNowOverlayPointDto> points) {}
