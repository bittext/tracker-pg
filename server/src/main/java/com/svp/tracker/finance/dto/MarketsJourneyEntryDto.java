package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record MarketsJourneyEntryDto(
        Long id,
        LocalDate periodDate,
        String periodLabel,
        BigDecimal targetAmount,
        BigDecimal actualAmount,
        String targetNote,
        String actualNote,
        /** actual - target; null if either side missing. */
        BigDecimal variance,
        /** ABOVE | ON | BELOW | UNKNOWN */
        String direction,
        Instant createdAt,
        Instant updatedAt) {}
