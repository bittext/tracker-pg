package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RobinhoodAgenticSyncedOrderDto(
        String accountNumber,
        String robinhoodOrderId,
        String symbol,
        String side,
        String orderType,
        BigDecimal quantity,
        BigDecimal limitPrice,
        BigDecimal averagePrice,
        String state,
        Instant createdAt,
        Instant updatedAt,
        Instant syncedAt) {}
