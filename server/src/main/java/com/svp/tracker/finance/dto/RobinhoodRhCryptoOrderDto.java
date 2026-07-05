package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RobinhoodRhCryptoOrderDto(
        long id,
        String status,
        String symbol,
        String tradingPair,
        String side,
        String orderType,
        BigDecimal quoteAmount,
        BigDecimal assetQuantity,
        BigDecimal estimatedNotional,
        String source,
        String robinhoodOrderId,
        String errorMessage,
        Instant createdAt,
        Instant placedAt) {}
