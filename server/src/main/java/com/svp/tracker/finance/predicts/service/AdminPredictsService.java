package com.svp.tracker.finance.predicts.service;

import com.svp.tracker.finance.predicts.config.FinancePredictsProperties;
import com.svp.tracker.finance.predicts.dto.admin.PredictsActionResultDto;
import com.svp.tracker.finance.predicts.dto.admin.PredictsAdminStatsDto;
import com.svp.tracker.finance.predicts.dto.admin.PredictsConfigDto;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Admin-only operations for Predicts: read configuration, fetch volume stats, and manually run the
 * scheduled jobs without waiting for the next cron tick. Delegates ingestion to the same services
 * used by the schedulers so manual and scheduled runs are identical paths.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPredictsService {

    private final FinancePredictsProperties props;
    private final JdbcTemplate jdbc;
    private final StockTwitsIngestService stocktwitsIngest;
    private final RedditIngestService redditIngest;
    private final PredictsBaselineService baselineService;
    private final PredictsService predictsService;

    /** Read-only mirror of properties — never includes secrets. */
    public PredictsConfigDto config() {
        FinancePredictsProperties.Reddit r = props.reddit();
        boolean redditCreds = r.clientId() != null && !r.clientId().isBlank()
                && r.clientSecret() != null && !r.clientSecret().isBlank();
        FinancePredictsProperties.X x = props.x();
        boolean xCreds = x.bearerToken() != null && !x.bearerToken().isBlank();
        return new PredictsConfigDto(
                props.enabled(),
                props.trackedTickerQuotaPerUser(),
                props.baselineWindowDays(),
                props.retention().mentionsDays(),
                new PredictsConfigDto.StocktwitsConfig(
                        props.stocktwits().enabled(),
                        props.stocktwits().baseUrl(),
                        props.stocktwits().maxMessagesPerSymbol(),
                        props.stocktwits().pollIntervalSeconds()),
                new PredictsConfigDto.RedditConfig(
                        r.enabled(),
                        r.userAgent(),
                        r.baseUrl(),
                        r.subredditList(),
                        r.postsPerSubreddit(),
                        r.pollIntervalSeconds(),
                        redditCreds),
                new PredictsConfigDto.XConfig(x.enabled(), x.baseUrl(), xCreds),
                new PredictsConfigDto.FinbertConfig(
                        props.finbert().enabled(),
                        props.finbert().baseUrl(),
                        props.finbert().maxBatchSize(),
                        props.finbert().timeoutMs()));
    }

    public PredictsAdminStatsDto stats() {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        long mentionsTotal = queryLong("SELECT COUNT(*) FROM finance_predicts_mentions");
        long mentions24h = queryLong(
                "SELECT COUNT(*) FROM finance_predicts_mentions WHERE posted_at >= ?",
                Timestamp.from(since));
        long uniqueSymbols = queryLong("SELECT COUNT(DISTINCT symbol) FROM finance_predicts_mentions");
        long uniqueAuthors24h = queryLong(
                "SELECT COUNT(DISTINCT author_hash) FROM finance_predicts_mentions WHERE posted_at >= ?",
                Timestamp.from(since));
        long bucketsTotal = queryLong("SELECT COUNT(*) FROM finance_predicts_buckets");
        long baselinesTotal = queryLong("SELECT COUNT(*) FROM finance_predicts_baselines");
        long trackedTotal = queryLong("SELECT COUNT(*) FROM finance_predicts_tickers");
        long trackedAuto = queryLong("SELECT COUNT(*) FROM finance_predicts_tickers WHERE auto_seeded = TRUE");

        Map<String, PredictsAdminStatsDto.PerSourceStat> perSource = new HashMap<>();
        jdbc.query(
                """
                SELECT source, COUNT(*) AS total,
                       SUM(CASE WHEN posted_at >= ? THEN 1 ELSE 0 END) AS recent,
                       COUNT(DISTINCT CASE WHEN posted_at >= ? THEN symbol END) AS recent_symbols,
                       MAX(posted_at) AS latest
                FROM finance_predicts_mentions
                GROUP BY source
                """,
                rs -> {
                    String source = rs.getString("source");
                    long total = rs.getLong("total");
                    long recent = rs.getLong("recent");
                    long recentSymbols = rs.getLong("recent_symbols");
                    Timestamp latest = rs.getTimestamp("latest");
                    perSource.put(
                            source,
                            new PredictsAdminStatsDto.PerSourceStat(
                                    source,
                                    total,
                                    recent,
                                    recentSymbols,
                                    latest == null ? null : latest.toInstant()));
                },
                Timestamp.from(since),
                Timestamp.from(since));
        List<PredictsAdminStatsDto.PerSourceStat> sources = new ArrayList<>(perSource.values());
        sources.sort((a, b) -> a.source().compareTo(b.source()));
        return new PredictsAdminStatsDto(
                Instant.now(),
                mentionsTotal,
                mentions24h,
                uniqueSymbols,
                uniqueAuthors24h,
                bucketsTotal,
                baselinesTotal,
                trackedTotal,
                trackedAuto,
                sources);
    }

    // ----------------------- Manual triggers -----------------------

    public PredictsActionResultDto runStocktwitsPoll() {
        try {
            stocktwitsIngest.pollCycle();
            return PredictsActionResultDto.ok(
                    "poll-stocktwits", "StockTwits poll cycle queued/finished — see server logs for per-symbol detail.", 0);
        } catch (Exception e) {
            log.warn("Manual StockTwits poll failed: {}", e.getMessage());
            return PredictsActionResultDto.failed("poll-stocktwits", e.getMessage());
        }
    }

    public PredictsActionResultDto runRedditPoll() {
        try {
            redditIngest.pollCycle();
            return PredictsActionResultDto.ok(
                    "poll-reddit",
                    props.reddit().enabled()
                            ? "Reddit poll cycle finished — see server logs for per-subreddit detail."
                            : "Reddit is disabled; the poll cycle no-opped.",
                    0);
        } catch (Exception e) {
            log.warn("Manual Reddit poll failed: {}", e.getMessage());
            return PredictsActionResultDto.failed("poll-reddit", e.getMessage());
        }
    }

    public PredictsActionResultDto runRecomputeBaselines() {
        try {
            int rows = baselineService.recomputeBaselines();
            return PredictsActionResultDto.ok(
                    "recompute-baselines",
                    "Recomputed " + rows + " baseline row(s) over the last " + props.baselineWindowDays() + " day(s).",
                    rows);
        } catch (Exception e) {
            log.warn("Manual baseline recompute failed: {}", e.getMessage());
            return PredictsActionResultDto.failed("recompute-baselines", e.getMessage());
        }
    }

    public PredictsActionResultDto runPurgeMentions() {
        try {
            int rows = baselineService.purgeOldMentions();
            return PredictsActionResultDto.ok(
                    "purge-mentions",
                    "Deleted " + rows + " mention(s) older than " + props.retention().mentionsDays() + " day(s).",
                    rows);
        } catch (Exception e) {
            log.warn("Manual mention purge failed: {}", e.getMessage());
            return PredictsActionResultDto.failed("purge-mentions", e.getMessage());
        }
    }

    public PredictsActionResultDto runAutoSeed() {
        try {
            int rows = predictsService.seedAutoTickersForAllUsers();
            return PredictsActionResultDto.ok(
                    "auto-seed",
                    "Auto-seeded " + rows + " new ticker(s) from Robinhood holdings.",
                    rows);
        } catch (Exception e) {
            log.warn("Manual auto-seed failed: {}", e.getMessage());
            return PredictsActionResultDto.failed("auto-seed", e.getMessage());
        }
    }

    private long queryLong(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }
}
