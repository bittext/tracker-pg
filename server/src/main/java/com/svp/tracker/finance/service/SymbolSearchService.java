package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.finance.dto.SymbolSearchMatchDto;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Alpha Vantage SYMBOL_SEARCH — resolves a free-text company name/ticker into candidate symbols
 * for the Markets → Research → Financials tab. Cached longer than quote/fundamentals data since
 * name-to-symbol mappings barely change.
 */
@Service
@Slf4j
public class SymbolSearchService {

    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final FinanceProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public SymbolSearchService(FinanceProperties props) {
        this.props = props;
    }

    public List<SymbolSearchMatchDto> search(String keywordsRaw) {
        String keywords = keywordsRaw == null ? "" : keywordsRaw.trim();
        if (keywords.isBlank()) {
            return List.of();
        }
        String key = props.alphaVantageApiKey();
        if (key == null || key.isBlank()) {
            return List.of();
        }
        String cacheKey = keywords.toLowerCase(java.util.Locale.ROOT);
        CacheEntry cached = cache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.atMs < CACHE_TTL.toMillis()) {
            return cached.matches;
        }
        List<SymbolSearchMatchDto> matches = fetch(keywords, key);
        if (matches != null) {
            cache.put(cacheKey, new CacheEntry(now, matches));
            return matches;
        }
        return cached != null ? cached.matches : List.of();
    }

    private List<SymbolSearchMatchDto> fetch(String keywords, String apiKey) {
        try {
            String url = props.alphaVantageBaseUrl()
                    + "?function=SYMBOL_SEARCH&keywords="
                    + URLEncoder.encode(keywords, StandardCharsets.UTF_8)
                    + "&apikey="
                    + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .timeout(Duration.ofMillis(Math.max(props.newsTimeoutMs(), 15_000)))
                    .header("Accept", "application/json")
                    .header("User-Agent", "tracker-server/1.0")
                    .build();
            HttpResponse<String> resp =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.warn("Alpha SYMBOL_SEARCH HTTP {} for {}", resp.statusCode(), keywords);
                return null;
            }
            JsonNode root = objectMapper.readTree(resp.body());
            if (!root.isObject() || root.has("Note") || root.has("Information") || root.has("Error Message")) {
                log.debug("Alpha SYMBOL_SEARCH unavailable for {} (rate limit or empty)", keywords);
                return null;
            }
            JsonNode best = root.path("bestMatches");
            List<SymbolSearchMatchDto> out = new ArrayList<>();
            if (best.isArray()) {
                for (JsonNode m : best) {
                    String symbol = text(m, "1. symbol");
                    if (symbol.isBlank()) {
                        continue;
                    }
                    out.add(new SymbolSearchMatchDto(
                            symbol, text(m, "2. name"), text(m, "4. region"), text(m, "3. type")));
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("Alpha SYMBOL_SEARCH failed for {}: {}", keywords, e.toString());
            return null;
        }
    }

    private static String text(JsonNode root, String field) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull()) {
            return "";
        }
        return n.asText("").trim();
    }

    private record CacheEntry(long atMs, List<SymbolSearchMatchDto> matches) {}
}
