package com.svp.tracker.finance.dto;

import java.time.Instant;
import java.util.List;

public record RobinhoodAgenticAutoTradeEvaluateDto(
        boolean ran,
        String message,
        int tickersEvaluated,
        int signalsGenerated,
        int ordersReviewed,
        int ordersPlaced,
        List<RobinhoodAgenticAutoTradeSignalDto> signals,
        Instant finishedAt) {}
