package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Speculative AI outlook for Then & now scenarios. Educational only — not investment advice.
 */
public record InvestmentThenNowOutlookDto(
        String disclaimer,
        int horizonMonths,
        String summary,
        String model,
        Instant generatedAt,
        boolean cached,
        List<InvestmentThenNowOutlookSymbolDto> symbols) {

    public record InvestmentThenNowOutlookSymbolDto(
            String symbol,
            String companyName,
            Long scenarioId,
            String thesis,
            ScenarioBand bull,
            ScenarioBand base,
            ScenarioBand bear,
            List<String> catalysts,
            List<String> risks,
            List<ForwardPoint> forwardBase,
            List<ForwardPoint> forwardBull,
            List<ForwardPoint> forwardBear) {}

    public record ScenarioBand(String narrative, BigDecimal targetPrice, String probabilityHint) {}

    public record ForwardPoint(LocalDate date, BigDecimal price) {}
}
