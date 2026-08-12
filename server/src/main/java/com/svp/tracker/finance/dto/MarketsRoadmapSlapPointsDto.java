package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Individual-account equity climb with $50k slap crossings and cash I/O notes. */
public record MarketsRoadmapSlapPointsDto(
        String accountSuffix,
        String accountLabel,
        BigDecimal stepAmount,
        BigDecimal latestTotal,
        LocalDate latestDate,
        LocalDate fromDate,
        LocalDate toDate,
        List<MarketsRoadmapSlapSeriesPointDto> series,
        List<BigDecimal> guideLevels,
        List<MarketsRoadmapSlapCrossingDto> crossings,
        List<MarketsRoadmapSlapCashNoteDto> cashNotes) {}
