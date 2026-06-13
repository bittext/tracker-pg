package com.svp.tracker.finance.dto;

import java.time.Instant;

public record RobinhoodAgenticAutoTradeRunDto(
        long id,
        Instant startedAt,
        Instant finishedAt,
        String status,
        int tickersEvaluated,
        int signalsGenerated,
        int ordersReviewed,
        int ordersPlaced,
        String message) {}
