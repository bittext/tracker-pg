package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RobinhoodRhCryptoAutoTradeEvaluateDto(
        boolean ran,
        String message,
        int tickersEvaluated,
        int signalsGenerated,
        int ordersAttempted,
        int ordersPlaced,
        List<RobinhoodRhCryptoAutoTradeSignalDto> signals,
        Instant finishedAt) {}
