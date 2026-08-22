package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MarketsJourneyLiveNetDto(
        LocalDate asOfDate,
        BigDecimal total,
        BigDecimal remaining,
        BigDecimal progressPct,
        BigDecimal priorTotal,
        BigDecimal dayChange,
        BigDecimal dayChangePct,
        List<MarketsJourneyLiveAccountDto> accounts,
        List<MarketsJourneyLiveSeriesPointDto> series,
        String note) {}
