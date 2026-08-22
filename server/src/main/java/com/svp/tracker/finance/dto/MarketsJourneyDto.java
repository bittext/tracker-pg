package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record MarketsJourneyDto(
        Long id,
        String title,
        BigDecimal milestoneAmount,
        int sortOrder,
        int entryCount,
        BigDecimal latestActual,
        BigDecimal progressPct,
        List<MarketsJourneyEntryDto> entries,
        MarketsJourneyLiveNetDto liveNet,
        Instant createdAt,
        Instant updatedAt) {}
