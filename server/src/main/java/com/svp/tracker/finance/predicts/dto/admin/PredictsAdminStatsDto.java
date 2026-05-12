package com.svp.tracker.finance.predicts.dto.admin;

import java.time.Instant;
import java.util.List;

/**
 * Snapshot of Predicts ingestion volume used by the Admin → Finance → Predicts panel.
 *
 * @param mentionsTotal {@code SELECT COUNT(*) FROM finance_predicts_mentions}
 * @param mentions24h same, scoped to {@code posted_at >= now() − 24h}
 * @param uniqueSymbols distinct {@code finance_predicts_mentions.symbol} count
 * @param uniqueAuthors24h distinct {@code author_hash} count in the last 24h
 * @param bucketsTotal {@code SELECT COUNT(*) FROM finance_predicts_buckets}
 * @param baselinesTotal {@code SELECT COUNT(*) FROM finance_predicts_baselines}
 * @param trackedTickersTotal sum of all {@code finance_predicts_tickers} rows across users
 * @param trackedTickersAutoSeeded subset where {@code auto_seeded = true}
 * @param perSource per-source mention counts and last fetch timestamps
 */
public record PredictsAdminStatsDto(
        Instant generatedAt,
        long mentionsTotal,
        long mentions24h,
        long uniqueSymbols,
        long uniqueAuthors24h,
        long bucketsTotal,
        long baselinesTotal,
        long trackedTickersTotal,
        long trackedTickersAutoSeeded,
        List<PerSourceStat> perSource) {

    public record PerSourceStat(
            String source,
            long mentionsTotal,
            long mentions24h,
            long uniqueSymbols24h,
            Instant lastMentionAt) {}
}
