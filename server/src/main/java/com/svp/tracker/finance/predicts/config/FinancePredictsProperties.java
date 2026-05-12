package com.svp.tracker.finance.predicts.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for Finance → Trading → Predicts: which community sources to poll, the FinBERT sidecar
 * coordinates, and retention / quota knobs. All flags default to safe values so a clean install can run
 * with `TRACKER_FINANCE_PREDICTS_ENABLED=true` and pick up StockTwits + heuristic sentiment immediately.
 *
 * @param enabled master switch; when false the scheduler, REST endpoints, and UI all hide
 * @param stocktwits StockTwits public REST polling (no auth required, rate-limited)
 * @param reddit Reddit OAuth ingestion (wired but disabled until credentials are provisioned)
 * @param x X / Twitter v2 ingestion (paid API tier; disabled by default)
 * @param finbert sentiment sidecar (Python FastAPI + ProsusAI/finbert); fallback heuristic kicks in when down
 * @param trackedTickerQuotaPerUser quota of manually added tickers per user (auto-seeded Robinhood tickers don't count)
 * @param baselineWindowDays days of bucket history used to compute per-hour-of-week mean/stddev baselines
 * @param retentionMentionsDays retention for raw mentions; aggregated buckets are kept indefinitely
 */
@ConfigurationProperties(prefix = "tracker.finance.predicts")
public record FinancePredictsProperties(
        boolean enabled,
        Stocktwits stocktwits,
        Reddit reddit,
        X x,
        Finbert finbert,
        int trackedTickerQuotaPerUser,
        int baselineWindowDays,
        Retention retention) {

    public FinancePredictsProperties {
        if (stocktwits == null) {
            stocktwits = new Stocktwits(true, 30, 1500, "https://api.stocktwits.com/api/2");
        }
        if (reddit == null) {
            reddit = new Reddit(false, "", "", "tracker-pg/finance-predicts", "https://oauth.reddit.com");
        }
        if (x == null) {
            x = new X(false, "", "https://api.twitter.com/2");
        }
        if (finbert == null) {
            finbert = new Finbert(true, "http://finbert:8000", 64, 3500);
        }
        if (trackedTickerQuotaPerUser <= 0) {
            trackedTickerQuotaPerUser = 25;
        }
        if (baselineWindowDays <= 0) {
            baselineWindowDays = 30;
        }
        if (retention == null) {
            retention = new Retention(60);
        }
    }

    /**
     * @param enabled poll StockTwits at all
     * @param maxMessagesPerSymbol cap per poll cycle (matches /streams/symbol max ≈ 30)
     * @param pollIntervalSeconds gap between full passes across all tracked tickers
     * @param baseUrl override for testing; production should leave the default
     */
    public record Stocktwits(boolean enabled, int maxMessagesPerSymbol, int pollIntervalSeconds, String baseUrl) {}

    /**
     * @param enabled poll Reddit at all (requires OAuth credentials)
     * @param clientId Reddit app client id (app type: script)
     * @param clientSecret Reddit app client secret
     * @param userAgent must include a contact identifier per Reddit API rules
     * @param baseUrl OAuth-authenticated host (default https://oauth.reddit.com)
     */
    public record Reddit(boolean enabled, String clientId, String clientSecret, String userAgent, String baseUrl) {}

    /**
     * @param enabled poll X / Twitter v2 (paid tier)
     * @param bearerToken app bearer token
     * @param baseUrl API base
     */
    public record X(boolean enabled, String bearerToken, String baseUrl) {}

    /**
     * @param enabled call the sidecar; false → use Java heuristic (cashtag lexicon + StockTwits native tags)
     * @param baseUrl reach the sidecar over the internal docker network (default http://finbert:8000)
     * @param maxBatchSize must be ≤ FINBERT_MAX_BATCH in the sidecar (default 64)
     * @param timeoutMs HTTP read timeout per batch call
     */
    public record Finbert(boolean enabled, String baseUrl, int maxBatchSize, int timeoutMs) {}

    /**
     * @param mentionsDays raw social mentions retention
     */
    public record Retention(int mentionsDays) {}
}
