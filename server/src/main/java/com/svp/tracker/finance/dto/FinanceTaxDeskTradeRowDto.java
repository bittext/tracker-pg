package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record FinanceTaxDeskTradeRowDto(
        String symbol,
        String side,
        BigDecimal quantity,
        BigDecimal averagePrice,
        BigDecimal notional,
        BigDecimal realizedPnl,
        String accountLabel,
        Instant executedAt) {}
