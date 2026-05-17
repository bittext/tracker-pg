package com.svp.tracker.finance.dto;

import java.math.BigDecimal;

public record RobinhoodQuarterlyGainDto(
        int quarter,
        String quarterLabel,
        BigDecimal realizedGain,
        BigDecimal estimatedTax) {}
