package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RobinhoodAgenticOrderDto(
        long id,
        String status,
        String symbol,
        String side,
        String orderType,
        BigDecimal quantity,
        BigDecimal amount,
        BigDecimal limitPrice,
        String timeInForce,
        BigDecimal estimatedNotional,
        String robinhoodOrderId,
        String errorMessage,
        Instant createdAt,
        Instant reviewedAt,
        Instant placedAt) {}
