package com.svp.tracker.finance.predicts.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.finance.predicts.config.FinancePredictsProperties;
import com.svp.tracker.finance.predicts.domain.PredictsBucket;
import com.svp.tracker.finance.predicts.domain.PredictsBucketSize;
import com.svp.tracker.finance.predicts.domain.PredictsMention;
import com.svp.tracker.finance.predicts.domain.PredictsSource;
import com.svp.tracker.finance.predicts.domain.PredictsSourceHealth;
import com.svp.tracker.finance.predicts.domain.PredictsTicker;
import com.svp.tracker.finance.predicts.dto.PredictsBucketPointDto;
import com.svp.tracker.finance.predicts.dto.PredictsLeaderboardDto;
import com.svp.tracker.finance.predicts.dto.PredictsLeaderboardEntryDto;
import com.svp.tracker.finance.predicts.dto.PredictsMentionDto;
import com.svp.tracker.finance.predicts.dto.PredictsSourceHealthDto;
import com.svp.tracker.finance.predicts.dto.PredictsSourceSummaryDto;
import com.svp.tracker.finance.predicts.dto.PredictsSymbolSummaryDto;
import com.svp.tracker.finance.predicts.dto.PredictsTickerDto;
import com.svp.tracker.finance.predicts.dto.PredictsTickerWriteRequest;
import com.svp.tracker.finance.predicts.dto.PredictsTimeseriesDto;
import com.svp.tracker.finance.predicts.repository.PredictsBaselineRepository;
import com.svp.tracker.finance.predicts.repository.PredictsBucketRepository;
import com.svp.tracker.finance.predicts.repository.PredictsMentionRepository;
import com.svp.tracker.finance.predicts.repository.PredictsTickerRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Façade for everything the Predicts UI and REST controller talks to: ticker CRUD, the Robinhood
 * auto-seeding job, and the read-side queries used by the source strip, leaderboard, per-symbol
 * summary, and bucket time series.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PredictsService {

    private static final int LEADERBOARD_DEFAULT_LIMIT = 20;
    private static final int MENTIONS_DEFAULT_LIMIT = 50;
    private static final int MENTIONS_MAX_LIMIT = 200;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final FinancePredictsProperties props;
    private final FinanceProperties financeProps;
    private final CurrentUserService currentUser;
    private final JdbcTemplate jdbc;
    private final PredictsTickerRepository tickerRepository;
    private final PredictsMentionRepository mentionRepository;
    private final PredictsBucketRepository bucketRepository;
    private final PredictsBaselineRepository baselineRepository;
    private final PredictsSourceHealthService sourceHealth;

    // ----------------------- Ticker CRUD -----------------------

    @Transactional(readOnly = true)
    public List<PredictsTickerDto> listMyTickers() {
        long userId = currentUser.requireUserId();
        return tickerRepository.findByOwnerUserIdOrderByAutoSeededAscSymbolAsc(userId).stream()
                .map(PredictsService::toDto)
                .toList();
    }

    @Transactional
    public PredictsTickerDto addTicker(PredictsTickerWriteRequest request) {
        long userId = currentUser.requireUserId();
        String symbol = sanitizeSymbol(request == null ? null : request.symbol());
        if (symbol.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbol is required");
        }
        long quota = props.trackedTickerQuotaPerUser();
        if (tickerRepository.countByOwnerUserIdAndAutoSeededFalse(userId) >= quota) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "tracked ticker quota reached (" + quota + " per user)");
        }
        Optional<PredictsTicker> existing = tickerRepository.findByOwnerUserIdAndSymbol(userId, symbol);
        PredictsTicker ticker = existing.orElseGet(PredictsTicker::new);
        ticker.setOwnerUserId(userId);
        ticker.setSymbol(symbol);
        ticker.setAutoSeeded(false);
        ticker.setSourcesEnabled(normalizeSources(request == null ? null : request.sourcesEnabled()));
        ticker.setNote(request == null ? null : request.note());
        return toDto(tickerRepository.save(ticker));
    }

    @Transactional
    public PredictsTickerDto updateTicker(long id, PredictsTickerWriteRequest request) {
        long userId = currentUser.requireUserId();
        PredictsTicker ticker = tickerRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ticker not found"));
        if (!ticker.getOwnerUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not your ticker");
        }
        if (request != null && request.sourcesEnabled() != null) {
            ticker.setSourcesEnabled(normalizeSources(request.sourcesEnabled()));
        }
        if (request != null) {
            ticker.setNote(request.note());
        }
        return toDto(tickerRepository.save(ticker));
    }

    @Transactional
    public void deleteTicker(long id) {
        long userId = currentUser.requireUserId();
        PredictsTicker ticker = tickerRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ticker not found"));
        if (!ticker.getOwnerUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not your ticker");
        }
        tickerRepository.delete(ticker);
    }

    // ----------------------- Auto-seed from Robinhood -----------------------

    /**
     * Once an hour (matching the alpha-vantage refresh cadence) we walk distinct symbols in the
     * configured Robinhood transactions table and ensure each is present as an auto-seeded ticker for
     * every user who's been signed in recently. We rely on `owner_user_id` on the rows; if the table
     * isn't multi-tenant we no-op (the manual flow still works).
     */
    /** Invoked by admin cron scheduler ({@code predicts.auto-seed}). */
    public void autoSeedFromRobinhood() {
        if (!props.enabled()) {
            return;
        }
        try {
            seedAutoTickersForAllUsers();
        } catch (Exception e) {
            log.warn("Predicts auto-seed from Robinhood skipped: {}", e.getMessage());
        }
    }

    @Transactional
    public int seedAutoTickersForAllUsers() {
        // Symbol → set of owner_user_ids that hold/traded it.
        Map<String, Set<Long>> ownersBySymbol = new LinkedHashMap<>();
        try {
            String sql = "SELECT DISTINCT owner_user_id, UPPER(TRIM("
                    + financeProps.stockSymbolColumn()
                    + ")) AS symbol FROM "
                    + financeProps.robinhoodTable()
                    + " WHERE owner_user_id IS NOT NULL AND "
                    + financeProps.stockSymbolColumn()
                    + " IS NOT NULL";
            jdbc.query(sql, rs -> {
                Long uid = rs.getLong("owner_user_id");
                String sym = rs.getString("symbol");
                if (uid != null && sym != null && !sym.isBlank()) {
                    ownersBySymbol.computeIfAbsent(sym, k -> new HashSet<>()).add(uid);
                }
            });
        } catch (Exception e) {
            log.debug("Robinhood auto-seed query failed (table may be empty or scoped differently): {}", e.getMessage());
            return 0;
        }
        int created = 0;
        for (Map.Entry<String, Set<Long>> entry : ownersBySymbol.entrySet()) {
            String symbol = entry.getKey();
            for (Long userId : entry.getValue()) {
                if (tickerRepository.existsByOwnerUserIdAndSymbol(userId, symbol)) {
                    continue;
                }
                PredictsTicker t = new PredictsTicker();
                t.setOwnerUserId(userId);
                t.setSymbol(symbol);
                t.setAutoSeeded(true);
                t.setSourcesEnabled("stocktwits");
                tickerRepository.save(t);
                created++;
            }
        }
        if (created > 0) {
            log.info("Predicts auto-seeded {} ticker(s) from Robinhood holdings", created);
        }
        return created;
    }

    // ----------------------- Read side: source health -----------------------

    public List<PredictsSourceHealthDto> listSourceHealth() {
        Set<PredictsSource> enabled = new HashSet<>();
        if (props.stocktwits().enabled()) {
            enabled.add(PredictsSource.STOCKTWITS);
        }
        if (props.reddit().enabled()) {
            enabled.add(PredictsSource.REDDIT);
        }
        if (props.x().enabled()) {
            enabled.add(PredictsSource.X);
        }
        Map<String, PredictsSourceHealth> rows = sourceHealth.listAll().stream()
                .collect(Collectors.toMap(PredictsSourceHealth::getSource, h -> h));
        List<PredictsSourceHealthDto> out = new ArrayList<>();
        for (PredictsSource src : PredictsSource.values()) {
            PredictsSourceHealth row = rows.get(src.wire());
            boolean enabledFlag = enabled.contains(src);
            if (row == null) {
                out.add(new PredictsSourceHealthDto(src.wire(), enabledFlag, null, null, null, null, 0, 0));
            } else {
                out.add(new PredictsSourceHealthDto(
                        src.wire(),
                        enabledFlag,
                        row.getLastAttemptAt(),
                        row.getLastSuccessAt(),
                        row.getLastErrorAt(),
                        row.getLastErrorMessage(),
                        row.getConsecutiveFailures(),
                        row.getMentionsIngested24h()));
            }
        }
        return out;
    }

    // ----------------------- Read side: per-symbol summary -----------------------

    @Transactional(readOnly = true)
    public PredictsSymbolSummaryDto summary(String rawSymbol) {
        String symbol = sanitizeSymbol(rawSymbol);
        if (symbol.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbol is required");
        }
        Instant since24h = Instant.now().minus(24, ChronoUnit.HOURS);
        Map<String, SourceAggregate> bySource = aggregateLast24h(symbol, since24h);
        List<PredictsSourceSummaryDto> sources = new ArrayList<>();
        BigDecimal blendedPositivity = BigDecimal.ZERO;
        int totalMentions = 0;
        int totalAuthors = 0;
        BigDecimal maxSpikeZ = BigDecimal.ZERO;
        Instant latest = null;
        for (PredictsSource src : PredictsSource.values()) {
            SourceAggregate agg = bySource.getOrDefault(src.wire(), SourceAggregate.EMPTY);
            BigDecimal posPct = positivityPct(agg.posCount, agg.negCount, agg.neuCount);
            BigDecimal spikeZ = computeSpikeZ(symbol, src.wire(), agg.lastHourCount);
            BigDecimal surgeZ = computeSurgeZ(symbol, src.wire(), agg.lastHourAuthors);
            sources.add(new PredictsSourceSummaryDto(
                    src.wire(),
                    agg.mentions,
                    agg.uniqueAuthors,
                    agg.posCount,
                    agg.negCount,
                    agg.neuCount,
                    agg.avg,
                    posPct,
                    spikeZ,
                    surgeZ));
            totalMentions += agg.mentions;
            totalAuthors += agg.uniqueAuthors;
            blendedPositivity = blendedPositivity.add(posPct.multiply(BigDecimal.valueOf(agg.mentions)));
            if (spikeZ.compareTo(maxSpikeZ) > 0) {
                maxSpikeZ = spikeZ;
            }
            if (agg.latest != null && (latest == null || agg.latest.isAfter(latest))) {
                latest = agg.latest;
            }
        }
        BigDecimal overallPosPct = totalMentions == 0
                ? BigDecimal.ZERO
                : blendedPositivity.divide(BigDecimal.valueOf(totalMentions), 2, RoundingMode.HALF_UP);
        BigDecimal hotScore = maxSpikeZ.multiply(overallPosPct).divide(HUNDRED, 4, RoundingMode.HALF_UP);
        return new PredictsSymbolSummaryDto(
                symbol, latest, totalMentions, totalAuthors, overallPosPct, maxSpikeZ, hotScore, sources);
    }

    // ----------------------- Read side: time series -----------------------

    @Transactional(readOnly = true)
    public PredictsTimeseriesDto timeseries(String rawSymbol, String bucketSizeWire, String sourceWire, int days) {
        String symbol = sanitizeSymbol(rawSymbol);
        if (symbol.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbol is required");
        }
        PredictsBucketSize size = PredictsBucketSize.fromWire(bucketSizeWire);
        int safeDays = Math.max(1, Math.min(60, days <= 0 ? 7 : days));
        Instant from = Instant.now().minus(safeDays, ChronoUnit.DAYS);
        Instant to = Instant.now();
        List<PredictsBucket> rows;
        if (sourceWire == null || sourceWire.isBlank() || "all".equalsIgnoreCase(sourceWire)) {
            rows = bucketRepository.findBySymbolAndBucketSizeAndBucketStartAfterOrderByBucketStartAsc(
                    symbol, size.wire(), from);
        } else {
            String src = PredictsSource.fromWire(sourceWire).wire();
            rows = bucketRepository.findBySymbolAndSourceAndBucketSizeAndBucketStartAfterOrderByBucketStartAsc(
                    symbol, src, size.wire(), from);
        }
        List<PredictsBucketPointDto> points = rows.stream()
                .map(b -> new PredictsBucketPointDto(
                        b.getBucketStart(),
                        b.getSource(),
                        b.getMsgCount(),
                        b.getUniqueAuthors(),
                        b.getPosCount(),
                        b.getNegCount(),
                        b.getNeuCount(),
                        b.getEngagementSum(),
                        b.getSentimentAvg()))
                .toList();
        return new PredictsTimeseriesDto(
                symbol,
                size.wire(),
                sourceWire == null ? "all" : sourceWire.toLowerCase(Locale.ROOT),
                from,
                to,
                points);
    }

    // ----------------------- Read side: mentions list -----------------------

    @Transactional(readOnly = true)
    public List<PredictsMentionDto> recentMentions(String rawSymbol, Integer limit) {
        String symbol = sanitizeSymbol(rawSymbol);
        if (symbol.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbol is required");
        }
        int cap = limit == null
                ? MENTIONS_DEFAULT_LIMIT
                : Math.max(1, Math.min(MENTIONS_MAX_LIMIT, limit));
        List<PredictsMention> rows = mentionRepository.findTop50BySymbolOrderByPostedAtDesc(symbol);
        return rows.stream()
                .limit(cap)
                .map(m -> new PredictsMentionDto(
                        m.getId(),
                        m.getSymbol(),
                        m.getSource(),
                        m.getBodyPreview() == null ? m.getBody() : m.getBodyPreview(),
                        m.getEngagementScore(),
                        m.getNativeSentiment(),
                        m.getSentimentLabel(),
                        m.getSentimentScore(),
                        m.getPostedAt(),
                        m.getUrl()))
                .toList();
    }

    // ----------------------- Read side: leaderboard -----------------------

    @Transactional(readOnly = true)
    public PredictsLeaderboardDto leaderboard(String type, Integer limit) {
        int cap = limit == null ? LEADERBOARD_DEFAULT_LIMIT : Math.max(1, Math.min(50, limit));
        Instant since24h = Instant.now().minus(24, ChronoUnit.HOURS);
        Map<String, SymbolAggregate> bySymbol = aggregateLast24hAcrossSymbols(since24h);
        List<PredictsLeaderboardEntryDto> entries = new ArrayList<>();
        for (Map.Entry<String, SymbolAggregate> entry : bySymbol.entrySet()) {
            String symbol = entry.getKey();
            SymbolAggregate agg = entry.getValue();
            BigDecimal pos = positivityPct(agg.posCount, agg.negCount, agg.neuCount);
            // Spike across all sources: take max z-score over each (symbol, source).
            BigDecimal spike = BigDecimal.ZERO;
            for (Map.Entry<String, Integer> srcEntry : agg.lastHourBySource.entrySet()) {
                BigDecimal z = computeSpikeZ(symbol, srcEntry.getKey(), srcEntry.getValue());
                if (z.compareTo(spike) > 0) {
                    spike = z;
                }
            }
            BigDecimal hot = spike.multiply(pos).divide(HUNDRED, 4, RoundingMode.HALF_UP);
            entries.add(new PredictsLeaderboardEntryDto(0, symbol, agg.mentions, agg.uniqueAuthors, pos, spike, hot));
        }
        Comparator<PredictsLeaderboardEntryDto> comp =
                switch (type == null ? "hot" : type.toLowerCase(Locale.ROOT)) {
                    case "positive" -> Comparator.comparing(PredictsLeaderboardEntryDto::positivityPct).reversed()
                            .thenComparing(PredictsLeaderboardEntryDto::mentions24h, Comparator.reverseOrder());
                    case "surge" -> Comparator.comparing(PredictsLeaderboardEntryDto::uniqueAuthors24h).reversed()
                            .thenComparing(PredictsLeaderboardEntryDto::mentions24h, Comparator.reverseOrder());
                    default -> Comparator.comparing(PredictsLeaderboardEntryDto::hotScore).reversed()
                            .thenComparing(PredictsLeaderboardEntryDto::spikeZ, Comparator.reverseOrder());
                };
        List<PredictsLeaderboardEntryDto> sorted = entries.stream()
                .sorted(comp)
                .limit(cap)
                .toList();
        List<PredictsLeaderboardEntryDto> ranked = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            PredictsLeaderboardEntryDto e = sorted.get(i);
            ranked.add(new PredictsLeaderboardEntryDto(
                    i + 1, e.symbol(), e.mentions24h(), e.uniqueAuthors24h(), e.positivityPct(), e.spikeZ(), e.hotScore()));
        }
        return new PredictsLeaderboardDto(type == null ? "hot" : type.toLowerCase(Locale.ROOT), Instant.now(), ranked);
    }

    // ----------------------- helpers -----------------------

    private Map<String, SourceAggregate> aggregateLast24h(String symbol, Instant since) {
        // (symbol, source, posted_at >= since) → counts + last-1h
        Map<String, SourceAggregate> out = new LinkedHashMap<>();
        Instant lastHourFrom = Instant.now().minus(1, ChronoUnit.HOURS);
        String sql = """
                SELECT source,
                       COUNT(*) AS mentions,
                       COUNT(DISTINCT author_hash) AS unique_authors,
                       SUM(CASE WHEN sentiment_label = 'positive' THEN 1 ELSE 0 END) AS pos_count,
                       SUM(CASE WHEN sentiment_label = 'negative' THEN 1 ELSE 0 END) AS neg_count,
                       SUM(CASE WHEN sentiment_label = 'neutral' OR sentiment_label IS NULL THEN 1 ELSE 0 END) AS neu_count,
                       COALESCE(AVG(sentiment_score), 0) AS avg_sent,
                       MAX(posted_at) AS latest,
                       SUM(CASE WHEN posted_at >= ? THEN 1 ELSE 0 END) AS last_hour_count,
                       COUNT(DISTINCT CASE WHEN posted_at >= ? THEN author_hash END) AS last_hour_authors
                FROM finance_predicts_mentions
                WHERE symbol = ? AND posted_at >= ?
                GROUP BY source
                """;
        jdbc.query(
                sql,
                rs -> {
                    String source = rs.getString("source");
                    SourceAggregate agg = new SourceAggregate();
                    agg.mentions = rs.getInt("mentions");
                    agg.uniqueAuthors = rs.getInt("unique_authors");
                    agg.posCount = rs.getInt("pos_count");
                    agg.negCount = rs.getInt("neg_count");
                    agg.neuCount = rs.getInt("neu_count");
                    BigDecimal avg = rs.getBigDecimal("avg_sent");
                    agg.avg = avg == null ? BigDecimal.ZERO : avg.setScale(4, RoundingMode.HALF_UP);
                    var ts = rs.getTimestamp("latest");
                    agg.latest = ts == null ? null : ts.toInstant();
                    agg.lastHourCount = rs.getInt("last_hour_count");
                    agg.lastHourAuthors = rs.getInt("last_hour_authors");
                    out.put(source, agg);
                },
                java.sql.Timestamp.from(lastHourFrom),
                java.sql.Timestamp.from(lastHourFrom),
                symbol,
                java.sql.Timestamp.from(since));
        return out;
    }

    private Map<String, SymbolAggregate> aggregateLast24hAcrossSymbols(Instant since) {
        Map<String, SymbolAggregate> out = new LinkedHashMap<>();
        Instant lastHourFrom = Instant.now().minus(1, ChronoUnit.HOURS);
        String sql = """
                SELECT symbol, source,
                       COUNT(*) AS mentions,
                       COUNT(DISTINCT author_hash) AS unique_authors,
                       SUM(CASE WHEN sentiment_label = 'positive' THEN 1 ELSE 0 END) AS pos_count,
                       SUM(CASE WHEN sentiment_label = 'negative' THEN 1 ELSE 0 END) AS neg_count,
                       SUM(CASE WHEN sentiment_label = 'neutral' OR sentiment_label IS NULL THEN 1 ELSE 0 END) AS neu_count,
                       SUM(CASE WHEN posted_at >= ? THEN 1 ELSE 0 END) AS last_hour_count
                FROM finance_predicts_mentions
                WHERE posted_at >= ?
                GROUP BY symbol, source
                """;
        jdbc.query(
                sql,
                rs -> {
                    String symbol = rs.getString("symbol");
                    String source = rs.getString("source");
                    SymbolAggregate agg = out.computeIfAbsent(symbol, k -> new SymbolAggregate());
                    int m = rs.getInt("mentions");
                    agg.mentions += m;
                    agg.uniqueAuthors += rs.getInt("unique_authors");
                    agg.posCount += rs.getInt("pos_count");
                    agg.negCount += rs.getInt("neg_count");
                    agg.neuCount += rs.getInt("neu_count");
                    agg.lastHourBySource.merge(source, rs.getInt("last_hour_count"), Integer::sum);
                },
                java.sql.Timestamp.from(lastHourFrom),
                java.sql.Timestamp.from(since));
        return out;
    }

    private BigDecimal computeSpikeZ(String symbol, String source, int observed) {
        return zScore(symbol, source, observed, true);
    }

    private BigDecimal computeSurgeZ(String symbol, String source, int observed) {
        return zScore(symbol, source, observed, false);
    }

    private BigDecimal zScore(String symbol, String source, int observed, boolean useMsgCount) {
        if (observed <= 0) {
            return BigDecimal.ZERO;
        }
        short hourOfWeek = currentHourOfWeek();
        return baselineRepository
                .findBySymbolAndSourceAndBucketSizeAndHourOfWeek(symbol, source, "1h", hourOfWeek)
                .map(b -> {
                    BigDecimal mean = useMsgCount ? b.getMsgCountMean() : b.getUniqueAuthorsMean();
                    BigDecimal sd = useMsgCount ? b.getMsgCountStddev() : b.getUniqueAuthorsStddev();
                    if (sd == null || sd.signum() == 0) {
                        return mean == null || mean.signum() == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(observed);
                    }
                    BigDecimal residual = BigDecimal.valueOf(observed).subtract(mean == null ? BigDecimal.ZERO : mean);
                    return residual.divide(sd, 4, RoundingMode.HALF_UP);
                })
                .orElse(BigDecimal.ZERO);
    }

    private static short currentHourOfWeek() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        // ISO DayOfWeek: 1=Monday..7=Sunday → convert to Postgres' DOW (0=Sunday..6=Saturday).
        int isoDow = now.getDayOfWeek().getValue();
        int pgDow = isoDow == 7 ? 0 : isoDow;
        return (short) (pgDow * 24 + now.getHour());
    }

    private static BigDecimal positivityPct(int pos, int neg, int neu) {
        int total = pos + neg + neu;
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal num = BigDecimal.valueOf(pos - neg);
        return num.multiply(HUNDRED).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private static PredictsTickerDto toDto(PredictsTicker t) {
        List<String> sources = Arrays.stream(
                        (t.getSourcesEnabled() == null ? "stocktwits" : t.getSourcesEnabled()).split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return new PredictsTickerDto(
                t.getId(),
                t.getSymbol(),
                t.isAutoSeeded(),
                sources,
                t.getNote(),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }

    private static String sanitizeSymbol(String input) {
        if (input == null) {
            return "";
        }
        String trimmed = input.trim().toUpperCase(Locale.ROOT);
        return trimmed.replaceAll("[^A-Z0-9_.\\-]", "");
    }

    private static String normalizeSources(List<String> wires) {
        if (wires == null || wires.isEmpty()) {
            return "stocktwits";
        }
        List<String> cleaned = new ArrayList<>();
        for (String w : wires) {
            if (w == null) {
                continue;
            }
            String t = w.trim().toLowerCase(Locale.ROOT);
            if (!t.isEmpty() && (t.equals("stocktwits") || t.equals("reddit") || t.equals("x"))
                    && !cleaned.contains(t)) {
                cleaned.add(t);
            }
        }
        return cleaned.isEmpty() ? "stocktwits" : String.join(",", cleaned);
    }

    // ----------------------- record-like aggregate holders -----------------------

    private static final class SourceAggregate {
        static final SourceAggregate EMPTY = new SourceAggregate();
        int mentions;
        int uniqueAuthors;
        int posCount;
        int negCount;
        int neuCount;
        BigDecimal avg = BigDecimal.ZERO;
        Instant latest;
        int lastHourCount;
        int lastHourAuthors;
    }

    private static final class SymbolAggregate {
        int mentions;
        int uniqueAuthors;
        int posCount;
        int negCount;
        int neuCount;
        Map<String, Integer> lastHourBySource = new HashMap<>();
    }
}
