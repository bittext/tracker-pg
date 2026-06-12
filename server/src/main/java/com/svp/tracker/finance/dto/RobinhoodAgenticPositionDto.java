package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RobinhoodAgenticPositionDto(
        String accountNumberMasked,
        String symbol,
        BigDecimal quantity,
        BigDecimal averageBuyPrice,
        BigDecimal marketValue,
        Instant syncedAt) {}
