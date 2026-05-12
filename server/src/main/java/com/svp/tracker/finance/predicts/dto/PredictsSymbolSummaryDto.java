package com.svp.tracker.finance.predicts.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Snapshot of one ticker rolled up across every enabled source.
 *
 * @param overallPositivityPct positivity blended across sources, weighted by mention count
 * @param overallSpikeZ max spike z-score across sources (the strongest attention signal wins)
 * @param hotScore composite ranking score used by the "hot now" leaderboard ({@code spikeZ * positivity / 100} clamped)
 * @param latestMentionAt latest mention timestamp across any source, or {@code null} when no mentions yet
 */
public record PredictsSymbolSummaryDto(
        String symbol,
        Instant latestMentionAt,
        int mentions24h,
        int uniqueAuthors24h,
        BigDecimal overallPositivityPct,
        BigDecimal overallSpikeZ,
        BigDecimal hotScore,
        List<PredictsSourceSummaryDto> sources) {}
