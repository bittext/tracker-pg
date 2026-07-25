package com.svp.tracker.finance.service;

import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.finance.dto.StockNewsAnalysisDto;
import com.svp.tracker.finance.dto.StockNewsDto;
import com.svp.tracker.finance.dto.StockNewsItemDto;
import com.svp.tracker.finance.dto.StockNewsStressSignalsDto;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockNewsService {
    private static final Pattern CONTROL_CHARS = Pattern.compile(".*\\p{Cntrl}+.*");
    private static final Pattern TAGS = Pattern.compile("<[^>]+>");
    private static final Set<String> POSITIVE_WORDS =
            Set.of(
                    "beat",
                    "beats",
                    "surge",
                    "rally",
                    "upside",
                    "upgrade",
                    "record",
                    "growth",
                    "expansion",
                    "profit",
                    "profits",
                    "strong",
                    "outperform",
                    "momentum");
    private static final Set<String> NEGATIVE_WORDS =
            Set.of(
                    "miss",
                    "misses",
                    "drop",
                    "decline",
                    "downgrade",
                    "loss",
                    "losses",
                    "weak",
                    "warning",
                    "risk",
                    "investigation",
                    "lawsuit",
                    "cuts",
                    "cut");
    private static final Set<String> GROWTH_POSITIVE_WORDS =
            Set.of("guidance", "forecast", "pipeline", "backlog", "expansion", "capex", "demand", "orders");
    private static final Set<String> GROWTH_NEGATIVE_WORDS =
            Set.of("slowdown", "recession", "headwinds", "uncertainty", "volatility", "pressure");
    private static final Set<String> MERGER_WORDS = Set.of("merger", "merge");
    private static final Set<String> ACQUISITION_WORDS = Set.of("acquisition", "acquire", "acquires", "acquired");
    private static final Set<String> DEAL_WORDS = Set.of("deal", "deals", "agreement", "partnership", "contract");
    private static final Set<String> PERMIT_WORDS = Set.of("permit", "permits", "approval", "approvals", "license", "licenses");
    private static final Set<String> SANCTION_WORDS = Set.of("sanction", "sanctions", "embargo", "blacklist", "penalty", "penalties");
    private static final Set<String> TRUSTED_SOURCES =
            Set.of(
                    "Reuters",
                    "Bloomberg",
                    "CNBC",
                    "The Wall Street Journal",
                    "Financial Times",
                    "MarketWatch",
                    "Yahoo Finance",
                    "Barron's",
                    "Investing.com",
                    "The Motley Fool",
                    "Associated Press",
                    "AP News",
                    "BBC",
                    "NPR",
                    "The New York Times",
                    "The Washington Post",
                    "The Guardian",
                    "NBC News",
                    "ABC News",
                    "CBS News",
                    "Politico",
                    "Axios",
                    "Al Jazeera");
    /**
     * If the article link resolves to one of these hosts (or subdomains), treat as trusted in strict mode when the
     * RSS {@code <source>} tag is missing or non-matching.
     */
    private static final List<String> TRUSTED_HOST_MARKERS =
            List.of(
                    "reuters.com",
                    "bloomberg.com",
                    "ft.com",
                    "wsj.com",
                    "nytimes.com",
                    "washingtonpost.com",
                    "apnews.com",
                    "ap.org",
                    "bbc.com",
                    "bbc.co.uk",
                    "cnbc.com",
                    "marketwatch.com",
                    "finance.yahoo.com",
                    "yahoo.com", // yahoo.com/finance paths
                    "investing.com",
                    "barrons.com",
                    "fool.com",
                    "theguardian.com",
                    "npr.org",
                    "axios.com",
                    "politico.com",
                    "aljazeera.com",
                    "nbcnews.com",
                    "cbsnews.com",
                    "abcnews"
            );
    private static final String FEED_NAME = "Google News RSS";
    private static final String FEED_URL = "https://news.google.com/rss/search";

    /**
     * Aggregated multi-feed reads are capped separately from {@code newsMaxItems}, which governs the single-feed
     * endpoint. Merging six feeds needs more headroom before dedupe trims the overlap.
     */
    private static final int AGGREGATE_MAX_ITEMS = 60;

    private static final int AGGREGATE_DEFAULT_ITEMS = 24;

    /** Words ignored when matching a company name against a headline. */
    private static final Set<String> COMPANY_NAME_STOPWORDS =
            Set.of(
                    "inc",
                    "inc.",
                    "corp",
                    "corp.",
                    "corporation",
                    "company",
                    "co",
                    "co.",
                    "ltd",
                    "ltd.",
                    "limited",
                    "plc",
                    "holdings",
                    "holding",
                    "group",
                    "the",
                    "and",
                    "class",
                    "common",
                    "stock",
                    "shares");

    private final FinanceProperties props;

    /** One RSS endpoint in the aggregated fan-out. */
    private record NewsFeedSource(String label, String url, boolean symbolScoped) {}

    /**
     * Fans out across mainstream wires, exchange feeds, and contributor/blog sources, then merges by recency. Unlike
     * {@link #fetchLatestNews}, results are not restricted to the trusted-outlet allowlist, so independent and personal
     * sites surface too; relevance is enforced instead by requiring the ticker or company name in broad search results.
     */
    /**
     * Yahoo Finance headline RSS for a single symbol — used by the research detail “Yahoo” news tab.
     */
    public StockNewsDto fetchYahooNews(String symbolRaw, String companyNameRaw, Integer limitRaw) {
        if (!props.newsEnabled()) {
            throw new IllegalStateException("Stock news endpoint is disabled (tracker.finance.news-enabled=false)");
        }
        String symbol = sanitizeSymbol(symbolRaw);
        if (symbol == null) {
            throw new IllegalArgumentException("Provide symbol");
        }
        String companyName = sanitizeCompanyName(companyNameRaw);
        int limit = sanitizeAggregateLimit(limitRaw);
        String sym = encode(symbol.toUpperCase(Locale.ROOT));
        NewsFeedSource feed = new NewsFeedSource(
                "Yahoo Finance",
                "https://feeds.finance.yahoo.com/rss/2.0/headline?s=" + sym + "&region=US&lang=en-US",
                true);
        List<StockNewsItemDto> items = dedupeAndSort(fetchFeedItems(feed, symbol, companyName), limit);
        // Fallback: Google News restricted to Yahoo Finance if the Yahoo RSS feed is empty/blocked.
        if (items.isEmpty()) {
            NewsFeedSource fallback = new NewsFeedSource(
                    "Yahoo via Google",
                    googleNewsUrl(symbol + " site:finance.yahoo.com when:14d"),
                    false);
            items = dedupeAndSort(fetchFeedItems(fallback, symbol, companyName), limit);
        }
        StockNewsAnalysisDto analysis = analyze(items);
        return new StockNewsDto(
                symbol,
                companyName,
                limit,
                items.size(),
                "Yahoo Finance",
                Instant.now().toString(),
                "Yahoo Finance headlines for " + symbol + ". Open finance.yahoo.com/quote/" + symbol + "/news for the full stream.",
                analysis,
                items);
    }

    public StockNewsDto fetchAggregatedNews(String symbolRaw, String companyNameRaw, Integer limitRaw) {
        if (!props.newsEnabled()) {
            throw new IllegalStateException("Stock news endpoint is disabled (tracker.finance.news-enabled=false)");
        }
        String symbol = sanitizeSymbol(symbolRaw);
        String companyName = sanitizeCompanyName(companyNameRaw);
        if (symbol == null && companyName == null) {
            throw new IllegalArgumentException("Provide symbol or companyName");
        }
        int limit = sanitizeAggregateLimit(limitRaw);
        List<NewsFeedSource> feeds = aggregateFeeds(symbol, companyName);

        List<StockNewsItemDto> merged = new ArrayList<>();
        List<String> contributing = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(feeds.size(), 6));
        try {
            Map<NewsFeedSource, Future<List<StockNewsItemDto>>> futures = new LinkedHashMap<>();
            for (NewsFeedSource feed : feeds) {
                futures.put(feed, pool.submit(() -> fetchFeedItems(feed, symbol, companyName)));
            }
            for (Map.Entry<NewsFeedSource, Future<List<StockNewsItemDto>>> entry : futures.entrySet()) {
                List<StockNewsItemDto> items = awaitFeed(entry.getKey(), entry.getValue());
                if (!items.isEmpty()) {
                    contributing.add(entry.getKey().label());
                    merged.addAll(items);
                }
            }
        } finally {
            pool.shutdownNow();
        }

        List<StockNewsItemDto> items = dedupeAndSort(merged, limit);
        StockNewsAnalysisDto analysis = analyze(items);
        String feedLabel = contributing.isEmpty() ? "Aggregated RSS" : "Aggregated: " + String.join(", ", contributing);
        return new StockNewsDto(
                symbol,
                companyName,
                limit,
                items.size(),
                feedLabel,
                Instant.now().toString(),
                "Merged headlines from wires, exchange feeds, and independent/contributor sites, deduplicated and"
                        + " sorted newest first, plus heuristic sentiment and stress signals.",
                analysis,
                items);
    }

    private List<StockNewsItemDto> fetchFeedItems(NewsFeedSource feed, String symbol, String companyName) {
        try {
            List<StockNewsItemDto> parsed = parseAndFilter(fetchFeedUrl(feed.url()), AGGREGATE_MAX_ITEMS, false);
            if (feed.symbolScoped()) {
                return parsed;
            }
            List<StockNewsItemDto> relevant = new ArrayList<>();
            for (StockNewsItemDto item : parsed) {
                if (mentionsCompany(item, symbol, companyName)) {
                    relevant.add(item);
                }
            }
            return relevant;
        } catch (Exception e) {
            log.debug("News feed {} unavailable: {}", feed.label(), e.toString());
            return List.of();
        }
    }

    private List<StockNewsItemDto> awaitFeed(NewsFeedSource feed, Future<List<StockNewsItemDto>> future) {
        try {
            return future.get(props.newsTimeoutMs() + 2_000L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (Exception e) {
            log.debug("News feed {} timed out: {}", feed.label(), e.toString());
            return List.of();
        }
    }

    private List<NewsFeedSource> aggregateFeeds(String symbol, String companyName) {
        List<NewsFeedSource> feeds = new ArrayList<>();
        feeds.add(new NewsFeedSource("Google News", googleNewsUrl(newsQuery(symbol, companyName)), false));
        feeds.add(new NewsFeedSource("Independent & blogs", googleNewsUrl(commentaryQuery(symbol, companyName)), false));
        feeds.add(new NewsFeedSource("Bing News", bingNewsUrl(newsQuery(symbol, companyName)), false));
        if (symbol != null) {
            String sym = encode(symbol.toUpperCase(Locale.ROOT));
            feeds.add(new NewsFeedSource(
                    "Yahoo Finance",
                    "https://feeds.finance.yahoo.com/rss/2.0/headline?s=" + sym + "&region=US&lang=en-US",
                    true));
            feeds.add(new NewsFeedSource("Nasdaq", "https://www.nasdaq.com/feed/rssoutbound?symbol=" + sym, true));
            feeds.add(new NewsFeedSource("Seeking Alpha", "https://seekingalpha.com/api/sa/combined/" + sym + ".xml", true));
        }
        return feeds;
    }

    /** Broad query aimed at analysis pieces, newsletters, and independent commentary rather than wire copy. */
    private static String commentaryQuery(String symbol, String companyName) {
        StringBuilder q = new StringBuilder();
        if (symbol != null) {
            q.append(symbol).append(' ');
        }
        if (companyName != null) {
            q.append('"').append(companyName).append('"').append(' ');
        }
        q.append("(analysis OR opinion OR thesis OR outlook OR valuation OR blog) when:14d");
        return q.toString().trim();
    }

    private static boolean mentionsCompany(StockNewsItemDto item, String symbol, String companyName) {
        String text = ((item.title() == null ? "" : item.title()) + " " + (item.summary() == null ? "" : item.summary()))
                .toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return false;
        }
        if (symbol != null && containsWord(text, symbol.toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (companyName == null) {
            return false;
        }
        for (String token : companyName.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (token.length() < 3 || COMPANY_NAME_STOPWORDS.contains(token)) {
                continue;
            }
            if (containsWord(text, token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsWord(String haystack, String needle) {
        if (needle.isBlank()) {
            return false;
        }
        int from = 0;
        while (true) {
            int at = haystack.indexOf(needle, from);
            if (at < 0) {
                return false;
            }
            boolean leftOk = at == 0 || !Character.isLetterOrDigit(haystack.charAt(at - 1));
            int end = at + needle.length();
            boolean rightOk = end >= haystack.length() || !Character.isLetterOrDigit(haystack.charAt(end));
            if (leftOk && rightOk) {
                return true;
            }
            from = at + 1;
        }
    }

    private static List<StockNewsItemDto> dedupeAndSort(List<StockNewsItemDto> items, int limit) {
        Set<String> seen = new HashSet<>();
        List<StockNewsItemDto> out = new ArrayList<>();
        for (StockNewsItemDto item : items) {
            if (!seen.add(dedupeKey(item))) {
                continue;
            }
            out.add(item);
        }
        out.sort(Comparator.comparing(StockNewsItemDto::publishedAt, Comparator.nullsLast(String::compareTo)).reversed());
        if (out.size() > limit) {
            return new ArrayList<>(out.subList(0, limit));
        }
        return out;
    }

    /** Same story syndicated across feeds arrives with different URLs, so match on the normalized headline. */
    private static String dedupeKey(StockNewsItemDto item) {
        String title = item.title() == null ? "" : item.title();
        String normalized = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        if (normalized.length() > 90) {
            normalized = normalized.substring(0, 90);
        }
        if (!normalized.isBlank()) {
            return normalized;
        }
        return item.url() == null ? "" : item.url().toLowerCase(Locale.ROOT);
    }

    private int sanitizeAggregateLimit(Integer raw) {
        if (raw == null) {
            return AGGREGATE_DEFAULT_ITEMS;
        }
        if (raw < 1) {
            return 1;
        }
        return Math.min(raw, AGGREGATE_MAX_ITEMS);
    }

    private static String googleNewsUrl(String query) {
        return FEED_URL + "?hl=en-US&gl=US&ceid=US:en&q=" + encode(query);
    }

    private static String bingNewsUrl(String query) {
        return "https://www.bing.com/news/search?format=RSS&q=" + encode(query);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public StockNewsDto fetchLatestNews(String symbolRaw, String companyNameRaw, Integer limitRaw) {
        if (!props.newsEnabled()) {
            throw new IllegalStateException("Stock news endpoint is disabled (tracker.finance.news-enabled=false)");
        }
        String symbol = sanitizeSymbol(symbolRaw);
        String companyName = sanitizeCompanyName(companyNameRaw);
        if (symbol == null && companyName == null) {
            throw new IllegalArgumentException("Provide symbol or companyName");
        }
        int limit = sanitizeLimit(limitRaw);
        String query = newsQuery(symbol, companyName);
        String rss = fetchFeed(query);
        List<StockNewsItemDto> items = parseAndFilter(rss, limit, true);
        StockNewsAnalysisDto analysis = analyze(items);
        return new StockNewsDto(
                symbol,
                companyName,
                limit,
                items.size(),
                FEED_NAME,
                Instant.now().toString(),
                "Latest validated headlines from trusted sources (up to 10) plus heuristic sentiment and stress signals.",
                analysis,
                items);
    }

    /**
     * General / topic RSS crawl (no ticker required): pass a full Google News query string, e.g. {@code
     * "(world news OR breaking) when:1d"}.
     */
    public StockNewsDto fetchTopicHeadlines(String topicLabel, String googleQuery, Integer limitRaw) {
        if (!props.newsEnabled()) {
            throw new IllegalStateException("Stock news endpoint is disabled (tracker.finance.news-enabled=false)");
        }
        if (topicLabel == null || topicLabel.isBlank()) {
            throw new IllegalArgumentException("topicLabel is required");
        }
        String q = sanitizeTopicQuery(googleQuery);
        int limit = sanitizeLimit(limitRaw);
        String rss = fetchFeed(q);
        // Topic / broad queries: do not require the small allowlist (many outlets fail exact match; Google may omit
        // &lt;source&gt; or use non-matching names). Stock-specific queries use strict mode above.
        List<StockNewsItemDto> items = parseAndFilter(rss, limit, false);
        StockNewsAnalysisDto analysis = analyze(items);
        return new StockNewsDto(
                "TOPIC",
                topicLabel,
                limit,
                items.size(),
                FEED_NAME,
                Instant.now().toString(),
                "Google News search: " + q + " (all RSS items with title+link, deduplicated; source label from feed or URL).",
                analysis,
                items);
    }

    private String sanitizeTopicQuery(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("query is required");
        }
        String s = raw.replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("query is empty");
        }
        if (CONTROL_CHARS.matcher(s).find()) {
            throw new IllegalArgumentException("query has unsupported control characters");
        }
        if (s.length() > 480) {
            throw new IllegalArgumentException("query is too long");
        }
        return s;
    }

    private static String newsQuery(String symbol, String companyName) {
        StringBuilder q = new StringBuilder();
        if (symbol != null) {
            q.append(symbol).append(' ');
        }
        if (companyName != null) {
            q.append('"').append(companyName).append('"').append(' ');
        }
        q.append("stock when:7d");
        return q.toString().trim();
    }

    private int sanitizeLimit(Integer raw) {
        int cap = props.newsMaxItems();
        if (raw == null) {
            return cap;
        }
        if (raw < 1) {
            return 1;
        }
        return Math.min(raw, cap);
    }

    private String sanitizeSymbol(String symbolRaw) {
        return sanitizeQueryTerm(symbolRaw, "symbol", 160);
    }

    private String sanitizeCompanyName(String companyNameRaw) {
        return sanitizeQueryTerm(companyNameRaw, "companyName", 220);
    }

    private static String sanitizeQueryTerm(String raw, String fieldName, int maxLength) {
        if (raw == null) {
            return null;
        }
        String s = raw.replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) {
            return null;
        }
        if (CONTROL_CHARS.matcher(s).matches()) {
            throw new IllegalArgumentException(fieldName + " has unsupported control characters");
        }
        if (s.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " is too long");
        }
        return s;
    }

    private static StockNewsAnalysisDto analyze(List<StockNewsItemDto> items) {
        if (items == null || items.isEmpty()) {
            return new StockNewsAnalysisDto(
                    "Neutral",
                    0.0,
                    50,
                    "Sideways",
                    new StockNewsStressSignalsDto(0, 0, 0, 0, 0, "Low"));
        }
        int positiveHits = 0;
        int negativeHits = 0;
        int growthPositiveHits = 0;
        int growthNegativeHits = 0;
        int mergerMentions = 0;
        int acquisitionMentions = 0;
        int dealMentions = 0;
        int permitMentions = 0;
        int sanctionMentions = 0;

        for (StockNewsItemDto item : items) {
            String text = ((item.title() == null ? "" : item.title()) + " " + (item.summary() == null ? "" : item.summary()))
                    .toLowerCase(Locale.ROOT);
            positiveHits += hitCount(text, POSITIVE_WORDS);
            negativeHits += hitCount(text, NEGATIVE_WORDS);
            growthPositiveHits += hitCount(text, GROWTH_POSITIVE_WORDS);
            growthNegativeHits += hitCount(text, GROWTH_NEGATIVE_WORDS);
            mergerMentions += hitCount(text, MERGER_WORDS);
            acquisitionMentions += hitCount(text, ACQUISITION_WORDS);
            dealMentions += hitCount(text, DEAL_WORDS);
            permitMentions += hitCount(text, PERMIT_WORDS);
            sanctionMentions += hitCount(text, SANCTION_WORDS);
        }

        double sentimentScore = boundedScore(positiveHits - negativeHits, Math.max(1, positiveHits + negativeHits));
        String sentimentLabel = sentimentLabel(sentimentScore);

        int growthSignal = (growthPositiveHits - growthNegativeHits) + (positiveHits - negativeHits) / 2;
        int projectedGrowthPercent = clamp(50 + growthSignal * 6, 0, 100);
        String growthLabel = growthLabel(projectedGrowthPercent);

        int stressTotal = mergerMentions + acquisitionMentions + dealMentions + permitMentions + sanctionMentions;
        String emphasis = stressTotal >= 8 ? "High" : stressTotal >= 4 ? "Moderate" : "Low";

        return new StockNewsAnalysisDto(
                sentimentLabel,
                sentimentScore,
                projectedGrowthPercent,
                growthLabel,
                new StockNewsStressSignalsDto(
                        mergerMentions,
                        acquisitionMentions,
                        dealMentions,
                        permitMentions,
                        sanctionMentions,
                        emphasis));
    }

    private static int hitCount(String text, Set<String> needles) {
        int c = 0;
        for (String needle : needles) {
            if (text.contains(needle)) {
                c += 1;
            }
        }
        return c;
    }

    private static double boundedScore(int net, int scale) {
        double raw = (double) net / (double) scale;
        if (raw > 1.0) {
            return 1.0;
        }
        if (raw < -1.0) {
            return -1.0;
        }
        return Math.round(raw * 100.0) / 100.0;
    }

    private static int clamp(int n, int min, int max) {
        return Math.max(min, Math.min(max, n));
    }

    private static String sentimentLabel(double score) {
        if (score >= 0.25) {
            return "Positive";
        }
        if (score <= -0.25) {
            return "Negative";
        }
        return "Neutral";
    }

    private static String growthLabel(int projectedGrowthPercent) {
        if (projectedGrowthPercent >= 67) {
            return "Bullish";
        }
        if (projectedGrowthPercent <= 33) {
            return "Cautious";
        }
        return "Sideways";
    }

    private String fetchFeed(String query) {
        return fetchFeedUrl(googleNewsUrl(query));
    }

    private String fetchFeedUrl(String url) {
        try {
            URI uri = URI.create(url);
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(props.newsTimeoutMs())).build();
            HttpRequest request =
                    HttpRequest.newBuilder(uri)
                            .GET()
                            .timeout(Duration.ofMillis(props.newsTimeoutMs()))
                            .header("Accept", "application/rss+xml, application/xml;q=0.9, text/xml;q=0.8")
                            .header("User-Agent", "tracker-server/1.0")
                            .build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new IllegalStateException("news feed request failed with status " + resp.statusCode());
            }
            return resp.body();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Could not fetch stock news feed", e);
            throw new IllegalStateException("Could not fetch stock news feed", e);
        }
    }

    /**
     * @param strictTrustedOnly when true, keep the legacy “trusted outlet” filter (improved with host hints). When
     *     false, accept any item with a title and link (topic crawls / broad Google queries).
     */
    private List<StockNewsItemDto> parseAndFilter(String xml, int limit, boolean strictTrustedOnly) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            // Allow resolving namespaced children (e.g. source) inside items.
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            NodeList itemNodes = doc.getElementsByTagName("item");

            List<StockNewsItemDto> out = new ArrayList<>();
            Set<String> dedupe = new HashSet<>();
            for (int i = 0; i < itemNodes.getLength(); i++) {
                Node n = itemNodes.item(i);
                if (!(n instanceof Element e)) {
                    continue;
                }
                String title = textOf(e, "title");
                String link = itemLink(e);
                String pub = normalizePubDate(textOf(e, "pubDate"));
                String publisherFromFeed = firstNonBlank(textOf(e, "source"), sourceFromAnyNamespace(e));
                String source = firstNonBlank(publisherFromFeed, inferSourceLabelFromUrl(link));
                String description = sanitizeSummary(textOf(e, "description"));
                if (title.isBlank() || link.isBlank()) {
                    continue;
                }
                if (source.isBlank()) {
                    source = "News";
                }
                if (strictTrustedOnly && !isStrictTrusted(publisherFromFeed, link)) {
                    continue;
                }
                String key = (title + "\u0000" + link).toLowerCase(Locale.ROOT);
                if (!dedupe.add(key)) {
                    continue;
                }
                out.add(new StockNewsItemDto(title, source, pub, link, description));
            }
            out.sort(Comparator.comparing(StockNewsItemDto::publishedAt, Comparator.nullsLast(String::compareTo)).reversed());
            if (out.size() > limit) {
                return new ArrayList<>(out.subList(0, limit));
            }
            return out;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Could not parse stock news feed", e);
            throw new IllegalStateException("Could not parse stock news feed", e);
        }
    }

    private static String itemLink(Element e) {
        String link = textOf(e, "link");
        if (!link.isBlank()) {
            return link;
        }
        String guid = textOf(e, "guid");
        if (guid == null) {
            return "";
        }
        String g = guid.trim();
        if (g.startsWith("http://") || g.startsWith("https://")) {
            return g;
        }
        return "";
    }

    private static String sourceFromAnyNamespace(Element e) {
        try {
            NodeList ns = e.getElementsByTagNameNS("*", "source");
            if (ns.getLength() == 0 || ns.item(0) == null) {
                return "";
            }
            String t = ns.item(0).getTextContent();
            return t == null ? "" : t.trim();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b == null || b.isBlank() ? "" : b;
    }

    private static String inferSourceLabelFromUrl(String link) {
        if (link == null || link.isBlank()) {
            return "";
        }
        try {
            URI u = URI.create(link);
            String host = u.getHost();
            if (host == null || host.isBlank()) {
                return "";
            }
            if (host.equalsIgnoreCase("news.google.com") || host.endsWith(".google.com") && host.contains("news")) {
                return "Google News";
            }
            String h = host.toLowerCase(Locale.ROOT);
            if (h.startsWith("www.")) {
                h = h.substring(4);
            }
            return h;
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    /**
     * Single-stock and symbol queries: require a known outlet name in the feed, a trusted target host (direct article
     * link), or skip Google redirect-only items we cannot attribute.
     */
    private static boolean isStrictTrusted(String publisherFromFeed, String link) {
        if (isTrustedSourceExactOrContains(publisherFromFeed)) {
            return true;
        }
        if (isNewsGoogleRedirectLink(link)) {
            return false;
        }
        return isTrustedHost(link);
    }

    private static boolean isNewsGoogleRedirectLink(String link) {
        if (link == null || link.isBlank()) {
            return false;
        }
        try {
            String h = URI.create(link).getHost();
            if (h == null) {
                return false;
            }
            h = h.toLowerCase(Locale.ROOT);
            return h.equals("news.google.com");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isTrustedHost(String link) {
        if (link == null || link.isBlank()) {
            return false;
        }
        String host;
        try {
            host = URI.create(link).getHost();
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (host == null || host.isBlank()) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        for (String m : TRUSTED_HOST_MARKERS) {
            if (h.contains(m)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTrustedSourceExactOrContains(String source) {
        if (source == null) {
            return false;
        }
        String s = source.trim();
        if (s.isEmpty()) {
            return false;
        }
        for (String trusted : TRUSTED_SOURCES) {
            if (s.equalsIgnoreCase(trusted)) {
                return true;
            }
        }
        String lower = s.toLowerCase(Locale.ROOT);
        for (String trusted : TRUSTED_SOURCES) {
            if (trusted.length() < 4) {
                continue;
            }
            if (lower.contains(trusted.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String normalizePubDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            return ZonedDateTime.parse(raw.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toString();
        } catch (RuntimeException ignored) {
            return raw.trim();
        }
    }

    private static String textOf(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        if (list.getLength() == 0 || list.item(0) == null) {
            return "";
        }
        String txt = list.item(0).getTextContent();
        return txt == null ? "" : txt.trim();
    }

    private static String sanitizeSummary(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String noTags = TAGS.matcher(raw).replaceAll(" ");
        String unescaped =
                noTags.replace("&nbsp;", " ")
                        .replace("&amp;", "&")
                        .replace("&quot;", "\"")
                        .replace("&#39;", "'")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">");
        String s = unescaped.replaceAll("\\s+", " ").trim();
        if (s.length() <= 280) {
            return s;
        }
        return s.substring(0, 277) + "...";
    }
}
