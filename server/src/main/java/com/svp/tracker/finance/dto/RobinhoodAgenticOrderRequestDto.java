package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record RobinhoodAgenticOrderRequestDto(
        String symbol,
        String side,
        String type,
        BigDecimal quantity,
        BigDecimal amount,
        BigDecimal limitPrice,
        String timeInForce) {}
