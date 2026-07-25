package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TradingJournalEntrySummaryDto(
        long id,
        LocalDate snapshotDate,
        String title,
        String bodyPreview,
        List<String> tags,
        Integer processGrade,
        Integer riskGrade,
        boolean linkedSummaryNote,
        boolean hasScheduledClose,
        BigDecimal closeCombinedTotal,
        BigDecimal closeCombinedChange,
        Instant closePulledAt,
        Instant updatedAt,
        int refCount,
        int attachmentCount) {}
