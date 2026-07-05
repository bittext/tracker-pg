package com.svp.tracker.finance.dto;

import java.time.Instant;

public record RobinhoodRhCryptoAutoTradeRunDto(
        long id,
        Instant startedAt,
        Instant finishedAt,
        String status,
        int tickersEvaluated,
        int signalsGenerated,
        int ordersAttempted,
        int ordersPlaced,
        String message) {}
