package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record RobinhoodAgenticPositionDto(
        String accountNumberMasked,
        String positionType,
        String positionKey,
        String symbol,
        String chainSymbol,
        String optionType,
        BigDecimal strikePrice,
        LocalDate expirationDate,
        BigDecimal quantity,
        BigDecimal averageBuyPrice,
        BigDecimal marketValue,
        Instant syncedAt) {}
