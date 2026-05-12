package com.svp.tracker.finance.predicts.service;

import com.svp.tracker.finance.predicts.config.FinancePredictsProperties;
import com.svp.tracker.finance.predicts.repository.PredictsMentionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nightly recompute of {@code finance_predicts_baselines} (mean & stddev of mention count and unique
 * authors per {@code (symbol, source, bucket_size, hour_of_week)}). Also enforces raw-mention retention.
 *
 * <p>The aggregation runs in pure SQL because PostgreSQL has a fast {@code stddev_samp()} and computing
 * hour-of-week via {@code EXTRACT} is far cheaper than streaming rows into the JVM. We restrict the
 * baseline window to {@code tracker.finance.predicts.baseline-window-days} so the stats track the
 * current community tempo (e.g. earnings season vs. quiet weeks).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PredictsBaselineService {

    private final FinancePredictsProperties props;
    private final JdbcTemplate jdbc;
    private final PredictsMentionRepository mentionRepository;

    /** Nightly at 03:17 UTC; jittered slightly so it doesn't collide with other midnight jobs. */
    @Scheduled(cron = "0 17 3 * * *", zone = "UTC")
    public void nightly() {
        if (!props.enabled()) {
            return;
        }
        recomputeBaselines();
        purgeOldMentions();
    }

    @Transactional
    public int recomputeBaselines() {
        int windowDays = Math.max(7, props.baselineWindowDays());
        Instant cutoff = Instant.now().minus(windowDays, ChronoUnit.DAYS);
        // Upsert one row per (symbol, source, bucket_size, hour_of_week) using mean/stddev over the window.
        // PostgreSQL EXTRACT(DOW): 0=Sunday..6=Saturday → hour_of_week ∈ [0..167].
        String sql = """
                INSERT INTO finance_predicts_baselines (symbol, source, bucket_size, hour_of_week,
                    msg_count_mean, msg_count_stddev, unique_authors_mean, unique_authors_stddev,
                    sample_size, updated_at)
                SELECT symbol, source, bucket_size,
                    (EXTRACT(DOW FROM bucket_start)::int * 24 +
                     EXTRACT(HOUR FROM bucket_start)::int)::smallint AS hour_of_week,
                    COALESCE(AVG(msg_count), 0)::numeric(10,4),
                    COALESCE(STDDEV_SAMP(msg_count), 0)::numeric(10,4),
                    COALESCE(AVG(unique_authors), 0)::numeric(10,4),
                    COALESCE(STDDEV_SAMP(unique_authors), 0)::numeric(10,4),
                    COUNT(*),
                    NOW()
                FROM finance_predicts_buckets
                WHERE bucket_start >= ?
                  AND bucket_size = '1h'
                GROUP BY symbol, source, bucket_size, hour_of_week
                ON CONFLICT ON CONSTRAINT uq_finance_predicts_baselines DO UPDATE
                SET msg_count_mean = EXCLUDED.msg_count_mean,
                    msg_count_stddev = EXCLUDED.msg_count_stddev,
                    unique_authors_mean = EXCLUDED.unique_authors_mean,
                    unique_authors_stddev = EXCLUDED.unique_authors_stddev,
                    sample_size = EXCLUDED.sample_size,
                    updated_at = NOW()
                """;
        int updated = jdbc.update(sql, java.sql.Timestamp.from(cutoff));
        log.info("Predicts baselines recomputed: {} row(s) within last {} day(s)", updated, windowDays);
        return updated;
    }

    @Transactional
    public int purgeOldMentions() {
        int retentionDays = Math.max(7, props.retention().mentionsDays());
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = mentionRepository.deleteByFetchedAtBefore(cutoff);
        log.info("Predicts mentions retention sweep: deleted {} row(s) older than {} day(s)", deleted, retentionDays);
        return deleted;
    }
}
