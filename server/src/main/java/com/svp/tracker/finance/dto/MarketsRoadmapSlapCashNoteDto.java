package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MarketsRoadmapSlapCashNoteDto(
        long id,
        LocalDate activityDate,
        String direction,
        BigDecimal amount,
        String note) {}
