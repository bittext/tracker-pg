package com.svp.tracker.finance.predicts.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.finance.predicts.config.FinancePredictsProperties;
import com.svp.tracker.finance.predicts.domain.PredictsMention;
import com.svp.tracker.finance.predicts.domain.PredictsSource;
import com.svp.tracker.finance.predicts.domain.PredictsTicker;
import com.svp.tracker.finance.predicts.dto.admin.PredictsStocktwitsProbeDto;
import com.svp.tracker.finance.predicts.repository.PredictsMentionRepository;
import com.svp.tracker.finance.predicts.repository.PredictsTickerRepository;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * StockTwits ingestion driver for Predicts. Polls the public {@code /streams/symbol/{ticker}.json}
 * endpoint for every distinct symbol across all tracked tickers (deduped across users), persists new
 * normalized mentions, scores them via the {@link SentimentScorer}, and folds counts into bucket
 * aggregates through {@link MentionBucketWriter}. Source health is recorded on each cycle so the
 * Predicts UI can show "last fetch" / error state.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockTwitsIngestService {

    private static final int MAX_BODY_PREVIEW = 240;
    private static final int PROBE_BODY_PREVIEW = 400;
    private static final int NOT_INDEXED_SAMPLE_CAP = 5;
    private static final String USER_AGENT =
            "tracker-pg/finance-predicts (+github.com/bittext/tracker-pg)";

    /**
     * Per-symbol poll result. {@code notIndexed} means the upstream returned 404 — almost always
     * because StockTwits doesn't index that ticker. We classify this as a benign condition because
     * one stale auto-seeded ticker (e.g. an OTC, foreign listing, or de-listed symbol) shouldn't
     * poison the entire source-health row. Real errors (5xx, 429, network) still throw.
     */
    private record PollOutcome(int ingested, boolean notIndexed) {}

    private final FinancePredictsProperties props;
    private final PredictsTickerRepository tickerRepository;
    private final PredictsMentionRepository mentionRepository;
    private final MentionBucketWriter bucketWriter;
    private final SentimentScorer sentimentScorer;
    private final PredictsSourceHealthService sourceHealth;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();

    /**
     * Fires every {@code tracker.finance.predicts.stocktwits.poll-interval-seconds}. Walks distinct tickers and
     * polls each in series with a small inter-symbol pause so we don't burst-trigger StockTwits' rate limit
     * (~200/hr unauthenticated). Each successful poll bumps {@code mentions_ingested_24h} on source health.
     */
    @Scheduled(
            fixedDelayString = "${tracker.finance.predicts.stocktwits.poll-interval-seconds:1500}000",
            initialDelayString = "${tracker.finance.predicts.stocktwits.poll-initial-delay-ms:60000}")
    public void pollCycle() {
        if (!props.enabled() || !props.stocktwits().enabled()) {
            sourceHealth.recordDisabled(PredictsSource.STOCKTWITS);
            return;
        }
        List<String> symbols = distinctTrackedSymbols();
        if (symbols.isEmpty()) {
            log.debug("StockTwits poll cycle: no tracked symbols");
            sourceHealth.recordSuccess(PredictsSource.STOCKTWITS, 0);
            return;
        }
        log.info("StockTwits poll cycle starting for {} symbol(s)", symbols.size());
        int totalIngested = 0;
        int totalErrors = 0;
        int totalNotIndexed = 0;
        List<String> notIndexedSample = new ArrayList<>();
        String lastErrorSymbol = null;
        String lastErrorMessage = null;
        for (String symbol : symbols) {
            try {
                PollOutcome outcome = pollSymbol(symbol);
                totalIngested += outcome.ingested();
                if (outcome.notIndexed()) {
                    totalNotIndexed++;
                    if (notIndexedSample.size() < NOT_INDEXED_SAMPLE_CAP) {
                        notIndexedSample.add(symbol);
                    }
                }
                // Small pause between symbols to spread out API calls.
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                totalErrors++;
                lastErrorSymbol = symbol;
                lastErrorMessage = e.getMessage();
                log.warn("StockTwits poll failed for {}: {}", symbol, e.getMessage());
            }
        }
        recordCycleHealth(symbols.size(), totalIngested, totalErrors, totalNotIndexed, notIndexedSample, lastErrorSymbol, lastErrorMessage);
        log.info(
                "StockTwits poll cycle complete: symbols={} ingested={} notIndexed={} errors={}",
                symbols.size(),
                totalIngested,
                totalNotIndexed,
                totalErrors);
    }

    private void recordCycleHealth(
            int totalSymbols,
            int totalIngested,
            int totalErrors,
            int totalNotIndexed,
            List<String> notIndexedSample,
            String lastErrorSymbol,
            String lastErrorMessage) {
        // Real transport / non-404 HTTP errors take precedence: include the most recent symbol in the
        // message so the admin can correlate UI text to logs.
        if (totalErrors > 0) {
            String msg = (lastErrorSymbol == null ? "" : "[" + lastErrorSymbol + "] ") + lastErrorMessage;
            sourceHealth.recordFailure(PredictsSource.STOCKTWITS, msg);
            return;
        }
        // Whole-cycle 404 means the upstream is reachable but rejecting every symbol. That's almost
        // always either a stale ticker set (rare) or — far more common in production — an IP-level
        // block on the egress (AWS / datacentre fingerprinting). Surface that distinctly.
        if (totalSymbols > 0 && totalNotIndexed == totalSymbols) {
            String sample = String.join(",", notIndexedSample);
            String msg = String.format(
                    Locale.ROOT,
                    "All %d tracked symbol(s) returned 404 from StockTwits — likely IP block (e.g. AWS egress) "
                            + "or all tracked tickers unindexed. Sample: %s. Run the admin Probe to confirm.",
                    totalSymbols,
                    sample);
            sourceHealth.recordFailure(PredictsSource.STOCKTWITS, msg);
            return;
        }
        // Mixed cycle (some 404, some 200) is healthy: the 404 symbols are simply unindexed. We
        // still log them so the admin can prune the ticker set if they want.
        if (totalNotIndexed > 0) {
            log.info(
                    "StockTwits poll: {}/{} symbol(s) returned 404 (treated as unindexed). Sample: {}",
                    totalNotIndexed,
                    totalSymbols,
                    notIndexedSample);
        }
        sourceHealth.recordSuccess(PredictsSource.STOCKTWITS, totalIngested);
    }

    private List<String> distinctTrackedSymbols() {
        Map<String, Boolean> dedup = new LinkedHashMap<>();
        for (PredictsTicker t : tickerRepository.findAll()) {
            if (t.getSymbol() == null || t.getSymbol().isBlank()) {
                continue;
            }
            if (!t.sourcesEnabledSet().contains(PredictsSource.STOCKTWITS)) {
                continue;
            }
            dedup.putIfAbsent(t.getSymbol().toUpperCase(Locale.ROOT), Boolean.TRUE);
        }
        return new ArrayList<>(dedup.keySet());
    }

    @Transactional
    public PollOutcome pollSymbol(String symbol) throws Exception {
        URI uri = symbolUri(symbol);
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        int status = resp.statusCode();
        if (status == 429) {
            throw new IllegalStateException("rate-limited by StockTwits (429)");
        }
        if (status == 404) {
            // Per-symbol "not indexed" — benign, no source-wide health impact.
            return new PollOutcome(0, true);
        }
        if (status / 100 != 2) {
            throw new IllegalStateException("StockTwits status " + status);
        }
        JsonNode root = objectMapper.readTree(resp.body());
        JsonNode messages = root.get("messages");
        if (messages == null || !messages.isArray() || messages.isEmpty()) {
            return new PollOutcome(0, false);
        }

        int cap = Math.max(1, props.stocktwits().maxMessagesPerSymbol());
        List<PredictsMention> fresh = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        int counted = 0;
        for (JsonNode msg : messages) {
            if (counted >= cap) {
                break;
            }
            counted++;
            String sourceMsgId = msg.has("id") ? String.valueOf(msg.get("id").asLong()) : null;
            if (sourceMsgId != null && mentionRepository.existsBySourceAndSourceMsgId(PredictsSource.STOCKTWITS.wire(), sourceMsgId)) {
                continue;
            }
            String body = textOr(msg, "body", "");
            if (body.isBlank()) {
                continue;
            }
            Instant postedAt = parseInstant(textOr(msg, "created_at", ""));
            if (postedAt == null) {
                postedAt = Instant.now();
            }
            JsonNode user = msg.get("user");
            String authorRaw = user == null ? null : textOr(user, "username", null);
            String nativeSentiment = parseNativeSentiment(msg);
            int likes = 0;
            JsonNode likesNode = msg.get("likes");
            if (likesNode != null && likesNode.has("total")) {
                likes = likesNode.get("total").asInt(0);
            }

            PredictsMention mention = new PredictsMention();
            mention.setSymbol(symbol);
            mention.setSource(PredictsSource.STOCKTWITS.wire());
            mention.setSourceMsgId(sourceMsgId);
            mention.setTextHash(sha256(body));
            mention.setBody(body);
            mention.setBodyPreview(truncate(body, MAX_BODY_PREVIEW));
            mention.setAuthorHash(authorRaw == null ? null : sha256(authorRaw.toLowerCase(Locale.ROOT)));
            mention.setEngagementScore(likes);
            mention.setNativeSentiment(nativeSentiment);
            mention.setPostedAt(postedAt);
            mention.setFetchedAt(Instant.now());
            // /streams/symbol response gives id but not URL; constructed best-effort link.
            if (sourceMsgId != null) {
                mention.setUrl("https://stocktwits.com/message/" + sourceMsgId);
            }
            fresh.add(mention);
            texts.add(body);
        }
        if (fresh.isEmpty()) {
            return new PollOutcome(0, false);
        }
        List<SentimentScore> scores = sentimentScorer.score(texts);
        applyScores(fresh, scores);
        mentionRepository.saveAll(fresh);
        bucketWriter.fold(symbol, PredictsSource.STOCKTWITS.wire(), fresh);
        return new PollOutcome(fresh.size(), false);
    }

    /**
     * Admin-only diagnostic call: performs a single direct request to the StockTwits stream endpoint
     * for {@code symbol} using the same client / URL / User-Agent that {@link #pollSymbol} uses, and
     * returns a structured snapshot of the outcome. Does not write to the database and does not
     * update source-health. Used to disambiguate "endpoint deprecated" vs "IP blocked" vs "symbol
     * unknown" without tailing server logs.
     */
    public PredictsStocktwitsProbeDto probe(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return new PredictsStocktwitsProbeDto(
                    symbol, null, USER_AGENT, 0, 0L, null, null, false, "symbol is required");
        }
        URI uri = symbolUri(normalized);
        long started = System.nanoTime();
        try {
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
            int status = resp.statusCode();
            String body = resp.body() == null ? "" : resp.body();
            String preview = truncate(body, PROBE_BODY_PREVIEW);
            Integer messageCount = null;
            String errorMessage = null;
            if (status / 100 == 2) {
                try {
                    JsonNode root = objectMapper.readTree(body);
                    JsonNode messages = root.get("messages");
                    messageCount = messages == null || !messages.isArray() ? 0 : messages.size();
                } catch (Exception parseError) {
                    errorMessage = "200 OK but body did not parse as StockTwits JSON: " + parseError.getMessage();
                }
            } else if (status == 404) {
                errorMessage = "404 Not Found — symbol unindexed at StockTwits, or IP-level block (AWS egress is commonly 404'd).";
            } else if (status == 429) {
                errorMessage = "429 Rate Limited — back off or use an authenticated key.";
            } else {
                errorMessage = "StockTwits status " + status;
            }
            return new PredictsStocktwitsProbeDto(
                    normalized,
                    uri.toString(),
                    USER_AGENT,
                    status,
                    elapsedMs,
                    preview,
                    messageCount,
                    false,
                    errorMessage);
        } catch (Exception e) {
            long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
            return new PredictsStocktwitsProbeDto(
                    normalized,
                    uri.toString(),
                    USER_AGENT,
                    0,
                    elapsedMs,
                    null,
                    null,
                    true,
                    "Transport error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private URI symbolUri(String symbol) {
        String base = stripTrailingSlash(props.stocktwits().baseUrl());
        return URI.create(base + "/streams/symbol/" + symbol + ".json");
    }

    private static void applyScores(List<PredictsMention> fresh, List<SentimentScore> scores) {
        for (int i = 0; i < fresh.size(); i++) {
            PredictsMention m = fresh.get(i);
            SentimentScore s = i < scores.size() ? scores.get(i) : SentimentScore.NEUTRAL_FALLBACK;
            String label = s.label();
            // Promote StockTwits native bullish/bearish over neutral model output when it disagrees with neutral —
            // the platform tag is an explicit user signal.
            if ("neutral".equalsIgnoreCase(label) && m.getNativeSentiment() != null) {
                label = m.getNativeSentiment();
            }
            m.setSentimentLabel(label);
            m.setSentimentScore(s.score());
            m.setConfidence(s.confidence() == null ? BigDecimal.ZERO : s.confidence());
        }
    }

    private static String parseNativeSentiment(JsonNode msg) {
        JsonNode entities = msg.get("entities");
        if (entities == null) {
            return null;
        }
        JsonNode sentiment = entities.get("sentiment");
        if (sentiment == null || sentiment.isNull()) {
            return null;
        }
        String basic = textOr(sentiment, "basic", "");
        if (basic.isBlank()) {
            return null;
        }
        String b = basic.toLowerCase(Locale.ROOT);
        if (b.contains("bull")) {
            return "positive";
        }
        if (b.contains("bear")) {
            return "negative";
        }
        return null;
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                return DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(value, Instant::from);
            } catch (Exception e) {
                return null;
            }
        }
    }

    private static String textOr(JsonNode node, String field, String def) {
        JsonNode child = node.get(field);
        return child == null || child.isNull() ? def : child.asText(def);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String stripTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "https://api.stocktwits.com/api/2";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
