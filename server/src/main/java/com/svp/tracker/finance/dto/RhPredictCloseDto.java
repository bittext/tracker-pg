package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RhPredictCloseDto(
        long id,
        String accountSuffix,
        String accountLabel,
        Instant closedAt,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal realized,
        String label) {}
