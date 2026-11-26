package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record RobinhoodCashIoEntryDto(
        long id,
        String accountSuffix,
        String accountLabel,
        LocalDate activityDate,
        String direction,
        BigDecimal amount,
        String note,
        String counterparty,
        String origin,
        String receiver,
        Instant createdAt,
        Instant updatedAt) {}
