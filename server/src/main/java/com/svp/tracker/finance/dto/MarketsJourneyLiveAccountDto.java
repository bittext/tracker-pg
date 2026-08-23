package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record MarketsJourneyLiveAccountDto(
        String accountSuffix,
        String label,
        String accountType,
        BigDecimal equityMarketValue,
        BigDecimal cashBalance,
        BigDecimal totalAccountValue,
        BigDecimal dayChange) {}
