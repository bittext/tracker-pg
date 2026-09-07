package com.svp.tracker.finance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/** One coin row within a crypto snapshot (future use). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RobinhoodRhCryptoHoldingDto(
        String symbol,
        BigDecimal quantity,
        BigDecimal averageBuyPrice,
        BigDecimal currentUnitPrice,
        BigDecimal marketValue,
        BigDecimal costBasis,
        BigDecimal unrealizedPnL,
        BigDecimal unrealizedPnLPercent,
        BigDecimal buyFees,
        BigDecimal lifetimeFees,
        BigDecimal sellFeeRate) {}
