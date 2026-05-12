package com.svp.tracker.finance.predicts.dto.admin;

import java.util.List;

/**
 * Read-only mirror of {@link com.svp.tracker.finance.predicts.config.FinancePredictsProperties}. Sensitive
 * fields (client secrets, bearer tokens) are <em>not</em> returned — only a {@code configured} flag so the
 * admin UI can show "credentials present" without leaking them.
 */
public record PredictsConfigDto(
        boolean enabled,
        int trackedTickerQuotaPerUser,
        int baselineWindowDays,
        int retentionMentionsDays,
        StocktwitsConfig stocktwits,
        RedditConfig reddit,
        XConfig x,
        FinbertConfig finbert) {

    public record StocktwitsConfig(boolean enabled, String baseUrl, int maxMessagesPerSymbol, int pollIntervalSeconds) {}

    public record RedditConfig(
            boolean enabled,
            String userAgent,
            String baseUrl,
            List<String> subreddits,
            int postsPerSubreddit,
            int pollIntervalSeconds,
            boolean credentialsConfigured) {}

    public record XConfig(boolean enabled, String baseUrl, boolean credentialsConfigured) {}

    public record FinbertConfig(boolean enabled, String baseUrl, int maxBatchSize, int timeoutMs) {}
}
