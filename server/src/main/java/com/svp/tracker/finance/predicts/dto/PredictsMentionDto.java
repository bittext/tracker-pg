package com.svp.tracker.finance.predicts.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Single mention surfaced in the drilldown. {@code body} is truncated to {@code body_preview} for the UI;
 * the full text stays on the server.
 */
public record PredictsMentionDto(
        long id,
        String symbol,
        String source,
        String bodyPreview,
        int engagementScore,
        String nativeSentiment,
        String sentimentLabel,
        BigDecimal sentimentScore,
        Instant postedAt,
        String url) {}
