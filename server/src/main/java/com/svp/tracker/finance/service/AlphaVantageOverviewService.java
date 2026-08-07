package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.finance.dto.CompanyResearchFundamentalsDto;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Alpha Vantage OVERVIEW for research dossiers (PE, short interest, ownership, sector/industry).
 * Cached briefly to stay within free-tier rate limits.
 */
@Service
@Slf4j
public class AlphaVantageOverviewService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(45);
    private static final String COVERAGE_NOTE =
            "Point-in-time Alpha Vantage OVERVIEW (not a daily short time-series). "
                    + "Industry-average PE and whale/dark-pool/options-flow are not included here — use Finviz Elite "
                    + "options/groups and the external chips for those.";

    private final FinanceProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public AlphaVantageOverviewService(FinanceProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public CompanyResearchFundamentalsDto overview(String symbolRaw) {
        String symbol = symbolRaw == null ? "" : symbolRaw.trim().toUpperCase(Locale.ROOT);
        if (symbol.isBlank()) {
            return null;
        }
        String key = props.alphaVantageApiKey();
        if (key == null || key.isBlank()) {
            return null;
        }
        CacheEntry cached = cache.get(symbol);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.atMs < CACHE_TTL.toMillis()) {
            return cached.dto;
        }
        CompanyResearchFundamentalsDto dto = fetch(symbol, key);
        if (dto != null) {
            cache.put(symbol, new CacheEntry(now, dto));
        }
        return dto;
    }

    private CompanyResearchFundamentalsDto fetch(String symbol, String apiKey) {
        try {
            String url = props.alphaVantageBaseUrl()
                    + "?function=OVERVIEW&symbol="
                    + URLEncoder.encode(symbol, StandardCharsets.UTF_8)
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
                log.warn("Alpha OVERVIEW HTTP {} for {}", resp.statusCode(), symbol);
                return null;
            }
            JsonNode root = objectMapper.readTree(resp.body());
            if (!root.isObject() || root.has("Note") || root.has("Information") || root.has("Error Message")) {
                log.debug("Alpha OVERVIEW unavailable for {} (rate limit or empty)", symbol);
                return null;
            }
            String name = text(root, "Name");
            if (name.isBlank()) {
                return null;
            }
            String description = text(root, "Description");
            if (description.length() > 900) {
                description = description.substring(0, 900) + "…";
            }
            return new CompanyResearchFundamentalsDto(
                    "alpha-vantage-overview",
                    name,
                    description.isBlank() ? null : description,
                    blankToNull(text(root, "Sector")),
                    blankToNull(text(root, "Industry")),
                    blankToNull(text(root, "MarketCapitalization")),
                    blankToNull(text(root, "PERatio")),
                    blankToNull(text(root, "ForwardPE")),
                    blankToNull(text(root, "PEGRatio")),
                    blankToNull(text(root, "EPS")),
                    blankToNull(text(root, "ProfitMargin")),
                    blankToNull(text(root, "OperatingMarginTTM")),
                    blankToNull(text(root, "ReturnOnEquityTTM")),
                    blankToNull(text(root, "RevenueTTM")),
                    blankToNull(text(root, "BookValue")),
                    blankToNull(text(root, "DividendYield")),
                    blankToNull(text(root, "Beta")),
                    blankToNull(text(root, "52WeekHigh")),
                    blankToNull(text(root, "52WeekLow")),
                    blankToNull(text(root, "AnalystTargetPrice")),
                    blankToNull(text(root, "ShortRatio")),
                    blankToNull(text(root, "ShortPercentFloat")),
                    blankToNull(text(root, "ShortPercentOutstanding")),
                    blankToNull(text(root, "PercentInsiders")),
                    blankToNull(text(root, "PercentInstitutions")),
                    blankToNull(text(root, "SharesOutstanding")),
                    blankToNull(text(root, "SharesFloat")),
                    COVERAGE_NOTE);
        } catch (Exception e) {
            log.warn("Alpha OVERVIEW failed for {}: {}", symbol, e.toString());
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

    private static String blankToNull(String v) {
        if (v == null || v.isBlank() || "None".equalsIgnoreCase(v) || "-".equals(v) || "null".equalsIgnoreCase(v)) {
            return null;
        }
        return v;
    }

    private record CacheEntry(long atMs, CompanyResearchFundamentalsDto dto) {}
}
