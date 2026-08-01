package com.svp.tracker.finance.dto.aitoken;

import java.util.List;

/** Markets → AI Token Factory dashboard. Heuristic pulse — not investment advice. */
public record AiTokenFactoryDashboardDto(
        String title,
        String fetchedAt,
        String note,
        String pulseNarrative,
        Double avgDayChangePercent,
        Double avgYtdChangePercent,
        int publicTickerCount,
        int privateNameCount,
        List<String> warnings,
        List<AiTokenFactoryLayerDto> layers) {}
