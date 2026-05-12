package com.svp.tracker.finance.predicts.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.finance.predicts.config.FinancePredictsProperties;
import com.svp.tracker.finance.predicts.domain.PredictsMention;
import com.svp.tracker.finance.predicts.domain.PredictsSource;
import com.svp.tracker.finance.predicts.repository.PredictsMentionRepository;
import com.svp.tracker.finance.predicts.repository.PredictsTickerRepository;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reddit ingestion driver for Predicts. Walks each configured subreddit's {@code /new.json} feed,
 * extracts cashtag mentions ({@code $TICKER}) that match the globally tracked set, persists each
 * (post, ticker) tuple as a single mention, scores via the shared {@link SentimentScorer}, and
 * folds the result into the same bucket tables used by {@link StockTwitsIngestService}.
 *
 * <p>One Reddit post can produce <em>multiple</em> mentions when it lists several tracked tickers;
 * each one uses {@code source_msg_id = "reddit-{post-id}-{ticker}"} to keep the per-source unique
 * key intact.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedditIngestService {

    private static final int MAX_BODY_PREVIEW = 240;
    /** $AAPL or AAPL after a space/start; 2..6 uppercase chars; conservative to avoid false positives. */
    private static final Pattern CASHTAG_RE = Pattern.compile("\\$([A-Z][A-Z0-9.\\-]{1,5})");
    private static final Pattern BARE_TICKER_RE =
            Pattern.compile("(?:(?<=\\s)|^)([A-Z]{2,5})(?:\\s|[.!?,;:]|$)");

    private final FinancePredictsProperties props;
    private final PredictsTickerRepository tickerRepository;
    private final PredictsMentionRepository mentionRepository;
    private final MentionBucketWriter bucketWriter;
    private final SentimentScorer sentimentScorer;
    private final PredictsSourceHealthService sourceHealth;
    private final RedditAuthService redditAuth;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();

    /**
     * Fires every {@code tracker.finance.predicts.reddit.poll-interval-seconds} (default 15 min). Each
     * cycle resolves the union of tracked tickers across users, then walks every configured subreddit
     * sequentially with a short inter-call pause to keep us under Reddit's per-app QPS guidance.
     */
    @Scheduled(
            fixedDelayString = "${tracker.finance.predicts.reddit.poll-interval-seconds:900}000",
            initialDelayString = "${tracker.finance.predicts.reddit.poll-initial-delay-ms:120000}")
    public void pollCycle() {
        if (!props.enabled() || !props.reddit().enabled()) {
            sourceHealth.recordDisabled(PredictsSource.REDDIT);
            return;
        }
        String token = redditAuth.currentBearer();
        if (token == null) {
            log.warn("Reddit poll skipped: no bearer token available (check client id / secret)");
            sourceHealth.recordFailure(PredictsSource.REDDIT, "no bearer token");
            return;
        }
        Set<String> tickerSet = trackedSymbolSet();
        if (tickerSet.isEmpty()) {
            log.debug("Reddit poll: no tracked tickers");
            sourceHealth.recordSuccess(PredictsSource.REDDIT, 0);
            return;
        }
        List<String> subreddits = props.reddit().subredditList();
        if (subreddits.isEmpty()) {
            log.debug("Reddit poll: no subreddits configured");
            sourceHealth.recordSuccess(PredictsSource.REDDIT, 0);
            return;
        }
        log.info("Reddit poll starting: {} subreddit(s), {} tracked ticker(s)", subreddits.size(), tickerSet.size());
        int totalIngested = 0;
        int totalErrors = 0;
        for (String sub : subreddits) {
            try {
                int ingested = pollSubreddit(sub, token, tickerSet);
                totalIngested += ingested;
                Thread.sleep(800);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                totalErrors++;
                log.warn("Reddit poll failed for r/{}: {}", sub, e.getMessage());
                sourceHealth.recordFailure(PredictsSource.REDDIT, "r/" + sub + ": " + e.getMessage());
            }
        }
        if (totalErrors == 0) {
            sourceHealth.recordSuccess(PredictsSource.REDDIT, totalIngested);
        }
        log.info(
                "Reddit poll complete: subreddits={} ingested={} errors={}",
                subreddits.size(),
                totalIngested,
                totalErrors);
    }

    @Transactional
    public int pollSubreddit(String subreddit, String bearer, Set<String> tickerSet) throws Exception {
        FinancePredictsProperties.Reddit reddit = props.reddit();
        int limit = Math.max(10, Math.min(100, reddit.postsPerSubreddit()));
        String base = stripTrailingSlash(reddit.baseUrl());
        URI uri = URI.create(base
                + "/r/"
                + URLEncoder.encode(subreddit, StandardCharsets.UTF_8)
                + "/new.json?limit="
                + limit);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", "Bearer " + bearer)
                .header("User-Agent", reddit.userAgent())
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status == 401 || status == 403) {
            throw new IllegalStateException("Reddit auth rejected (status " + status + ")");
        }
        if (status == 429) {
            throw new IllegalStateException("rate-limited by Reddit (429)");
        }
        if (status / 100 != 2) {
            throw new IllegalStateException("Reddit status " + status);
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode children = root.path("data").path("children");
        if (!children.isArray() || children.isEmpty()) {
            return 0;
        }
        List<PredictsMention> fresh = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        Map<String, List<PredictsMention>> bySymbol = new HashMap<>();
        for (JsonNode child : children) {
            JsonNode post = child.path("data");
            String postId = post.path("id").asText(null);
            if (postId == null || postId.isBlank()) {
                continue;
            }
            String title = post.path("title").asText("");
            String selfText = post.path("selftext").asText("");
            if (title.isBlank() && selfText.isBlank()) {
                continue;
            }
            String combined = (title + "\n\n" + selfText).trim();
            Set<String> matched = extractTickers(combined, tickerSet);
            if (matched.isEmpty()) {
                continue;
            }
            long createdUtcSec = post.path("created_utc").asLong(Instant.now().getEpochSecond());
            Instant postedAt = Instant.ofEpochSecond(createdUtcSec);
            String author = post.path("author").asText("");
            int score = post.path("score").asInt(0);
            int comments = post.path("num_comments").asInt(0);
            String permalinkRaw = post.path("permalink").asText("");
            String url = permalinkRaw.isBlank() ? null : "https://www.reddit.com" + permalinkRaw;

            for (String symbol : matched) {
                String compositeId = "reddit-" + postId + "-" + symbol;
                if (mentionRepository.existsBySourceAndSourceMsgId(PredictsSource.REDDIT.wire(), compositeId)) {
                    continue;
                }
                PredictsMention mention = new PredictsMention();
                mention.setSymbol(symbol);
                mention.setSource(PredictsSource.REDDIT.wire());
                mention.setSourceMsgId(compositeId);
                mention.setTextHash(sha256(combined));
                mention.setBody(combined);
                mention.setBodyPreview(truncate(combined, MAX_BODY_PREVIEW));
                mention.setAuthorHash(
                        author == null || author.isBlank()
                                ? null
                                : sha256(author.toLowerCase(Locale.ROOT)));
                // Engagement: combine score and comments into a single ranked number; clamp at 0.
                mention.setEngagementScore(Math.max(0, score + comments));
                mention.setNativeSentiment(null);
                mention.setPostedAt(postedAt);
                mention.setFetchedAt(Instant.now());
                mention.setUrl(url);
                fresh.add(mention);
                texts.add(combined);
                bySymbol.computeIfAbsent(symbol, k -> new ArrayList<>()).add(mention);
            }
        }
        if (fresh.isEmpty()) {
            return 0;
        }
        List<SentimentScore> scores = sentimentScorer.score(texts);
        for (int i = 0; i < fresh.size(); i++) {
            PredictsMention m = fresh.get(i);
            SentimentScore s = i < scores.size() ? scores.get(i) : SentimentScore.NEUTRAL_FALLBACK;
            m.setSentimentLabel(s.label());
            m.setSentimentScore(s.score());
            m.setConfidence(s.confidence() == null ? BigDecimal.ZERO : s.confidence());
        }
        mentionRepository.saveAll(fresh);
        for (Map.Entry<String, List<PredictsMention>> entry : bySymbol.entrySet()) {
            bucketWriter.fold(entry.getKey(), PredictsSource.REDDIT.wire(), entry.getValue());
        }
        return fresh.size();
    }

    private Set<String> trackedSymbolSet() {
        Set<String> out = new HashSet<>();
        tickerRepository.findAll().forEach(t -> {
            if (t.getSymbol() == null || t.getSymbol().isBlank()) {
                return;
            }
            if (!t.sourcesEnabledSet().contains(PredictsSource.REDDIT)
                    && !t.sourcesEnabledSet().contains(PredictsSource.STOCKTWITS)) {
                // Reddit ingestion always considers a ticker if at least one source is enabled — keeping the matching set
                // small enough for the bare-ticker regex to stay precise. Per-source enablement controls fine-grained
                // ingestion downstream of this set.
                return;
            }
            out.add(t.getSymbol().toUpperCase(Locale.ROOT));
        });
        return out;
    }

    /**
     * Extracts the set of tracked tickers referenced in {@code text}. Cashtags ({@code $XYZ}) match
     * unconditionally; bare uppercase words only match when they're already in {@code trackedSet} to
     * avoid false positives on words like "BUY" or "DD".
     */
    static Set<String> extractTickers(String text, Set<String> trackedSet) {
        if (text == null || text.isBlank() || trackedSet.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        Matcher cash = CASHTAG_RE.matcher(text);
        while (cash.find()) {
            String t = cash.group(1).toUpperCase(Locale.ROOT);
            if (trackedSet.contains(t)) {
                out.add(t);
            }
        }
        Matcher bare = BARE_TICKER_RE.matcher(text);
        while (bare.find()) {
            String t = bare.group(1).toUpperCase(Locale.ROOT);
            if (trackedSet.contains(t)) {
                out.add(t);
            }
        }
        return out;
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
            return "https://oauth.reddit.com";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
