package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MarketsJourneyEntryWriteRequest(
        LocalDate periodDate,
        String periodLabel,
        BigDecimal targetAmount,
        BigDecimal actualAmount,
        String targetNote,
        String actualNote) {}
