package com.svp.tracker.finance.dto;

import java.time.Instant;
import java.time.LocalDate;

public record RobinhoodSelectiveTradeEntryDto(
        long id,
        LocalDate activityDate,
        String symbol,
        String outcome,
        String note,
        String accountSuffix,
        Instant createdAt,
        Instant updatedAt) {}
