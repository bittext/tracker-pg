package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record RobinhoodTradeInterestRequestDto(
        String instrumentKind,
        String symbol,
        Instant plannedAt,
        BigDecimal underlyingPrice,
        BigDecimal contractTargetPrice,
        LocalDate expiryDate,
        String note,
        String status) {}
