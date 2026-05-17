package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.util.List;

/** Quarterly realized gains and a simple estimated-tax projection (not tax advice). */
public record RobinhoodPerformanceTaxDto(
        List<RobinhoodQuarterlyGainDto> quarterlyGains,
        BigDecimal yearRealizedGain,
        double estimatedTaxRate,
        BigDecimal estimatedTaxOwed,
        String disclaimer) {}
