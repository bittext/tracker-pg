package com.svp.tracker.finance.predicts.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.finance.predicts.config.FinancePredictsProperties;
import com.svp.tracker.finance.predicts.domain.PredictsMention;
import com.svp.tracker.finance.predicts.domain.PredictsSource;
import com.svp.tracker.finance.predicts.domain.PredictsTicker;
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
        for (String symbol : symbols) {
            try {
                int ingested = pollSymbol(symbol);
                totalIngested += ingested;
                // Small pause between symbols to spread out API calls.
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                totalErrors++;
                log.warn("StockTwits poll failed for {}: {}", symbol, e.getMessage());
                sourceHealth.recordFailure(PredictsSource.STOCKTWITS, e.getMessage());
            }
        }
        if (totalErrors == 0) {
            sourceHealth.recordSuccess(PredictsSource.STOCKTWITS, totalIngested);
        }
        log.info(
                "StockTwits poll cycle complete: symbols={} ingested={} errors={}",
                symbols.size(),
                totalIngested,
                totalErrors);
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
    public int pollSymbol(String symbol) throws Exception {
        String base = stripTrailingSlash(props.stocktwits().baseUrl());
        URI uri = URI.create(base + "/streams/symbol/" + symbol + ".json");
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "tracker-pg/finance-predicts (+github.com/bittext/tracker-pg)")
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        int status = resp.statusCode();
        if (status == 429) {
            throw new IllegalStateException("rate-limited by StockTwits (429)");
        }
        if (status / 100 != 2) {
            throw new IllegalStateException("StockTwits status " + status);
        }
        JsonNode root = objectMapper.readTree(resp.body());
        JsonNode messages = root.get("messages");
        if (messages == null || !messages.isArray() || messages.isEmpty()) {
            return 0;
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
            return 0;
        }
        List<SentimentScore> scores = sentimentScorer.score(texts);
        applyScores(fresh, scores);
        mentionRepository.saveAll(fresh);
        bucketWriter.fold(symbol, PredictsSource.STOCKTWITS.wire(), fresh);
        return fresh.size();
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
