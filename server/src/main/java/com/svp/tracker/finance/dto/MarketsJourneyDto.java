package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record MarketsJourneyDto(
        Long id,
        String title,
        BigDecimal milestoneAmount,
        int sortOrder,
        /** Day a chapter began, or null for a journey that follows the full Daily Tracker history. */
        LocalDate startDate,
        /** Net across every account at the start of a chapter, or null. */
        BigDecimal startAmount,
        int entryCount,
        BigDecimal latestActual,
        BigDecimal progressPct,
        List<MarketsJourneyEntryDto> entries,
        MarketsJourneyLiveNetDto liveNet,
        Instant createdAt,
        Instant updatedAt) {}
