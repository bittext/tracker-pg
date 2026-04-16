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
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
                    "The Motley Fool");
    private static final String FEED_NAME = "Google News RSS";
    private static final String FEED_URL = "https://news.google.com/rss/search";

    private final FinanceProperties props;

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
        List<StockNewsItemDto> items = parseAndFilter(rss, limit);
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
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            URI uri = URI.create(FEED_URL + "?hl=en-US&gl=US&ceid=US:en&q=" + encoded);
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

    private List<StockNewsItemDto> parseAndFilter(String xml, int limit) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
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
                String link = textOf(e, "link");
                String pub = normalizePubDate(textOf(e, "pubDate"));
                String source = textOf(e, "source");
                String description = sanitizeSummary(textOf(e, "description"));
                if (title.isBlank() || link.isBlank() || source.isBlank()) {
                    continue;
                }
                if (!isTrustedSource(source)) {
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

    private static boolean isTrustedSource(String source) {
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
        return false;
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
