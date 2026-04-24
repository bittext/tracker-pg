package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.finance.dto.YahooExtendedQuoteDto;
import com.svp.tracker.finance.dto.YahooSimpleQuoteDto;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Fetches regular session quote fields for several tickers in one request (Yahoo v7). Not investment advice; for UI
 * context only.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class YahooBatchQuoteService {
    private static final String QUOTE = "https://query1.finance.yahoo.com/v7/finance/quote?symbols=";

    private final FinanceProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, YahooSimpleQuoteDto> fetchBySymbols(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return Map.of();
        }
        String joined =
                String.join(
                        ",",
                        symbols.stream()
                                .map(s -> s == null ? "" : s.trim().toUpperCase(Locale.ROOT))
                                .filter(s -> !s.isEmpty())
                                .toList());
        if (joined.isEmpty()) {
            return Map.of();
        }
        String enc = URLEncoder.encode(joined, StandardCharsets.UTF_8);
        String url = QUOTE + enc;
        try {
            HttpClient client =
                    HttpClient.newBuilder().connectTimeout(Duration.ofMillis(props.newsTimeoutMs())).build();
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(url))
                            .GET()
                            .timeout(Duration.ofMillis(props.newsTimeoutMs()))
                            .header("Accept", "application/json")
                            .header("User-Agent", "tracker-server/1.0")
                            .build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.warn("Yahoo quote batch HTTP {}", resp.statusCode());
                return Map.of();
            }
            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode result = root.path("quoteResponse").path("result");
            if (!result.isArray() || result.isEmpty()) {
                return Map.of();
            }
            Map<String, YahooSimpleQuoteDto> out = new HashMap<>();
            for (JsonNode n : result) {
                if (n == null || n.isNull()) {
                    continue;
                }
                String sym = text(n, "symbol");
                if (sym.isEmpty()) {
                    continue;
                }
                Double px = dbl(n, "regularMarketPrice");
                Double chg = dbl(n, "regularMarketChangePercent");
                out.put(
                        sym,
                        new YahooSimpleQuoteDto(sym, text(n, "shortName"), px, chg));
            }
            return out;
        } catch (Exception e) {
            log.warn("Yahoo batch quote failed", e);
            return Map.of();
        }
    }

    /**
     * Same v7 batch endpoint, parsed for KPI / sector fields used in swing narratives. Missing fields stay null.
     */
    public Map<String, YahooExtendedQuoteDto> fetchExtendedBySymbols(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return Map.of();
        }
        String joined =
                String.join(
                        ",",
                        symbols.stream()
                                .map(s -> s == null ? "" : s.trim().toUpperCase(Locale.ROOT))
                                .filter(s -> !s.isEmpty())
                                .toList());
        if (joined.isEmpty()) {
            return Map.of();
        }
        String enc = URLEncoder.encode(joined, StandardCharsets.UTF_8);
        String url = QUOTE + enc;
        try {
            HttpClient client =
                    HttpClient.newBuilder().connectTimeout(Duration.ofMillis(props.newsTimeoutMs())).build();
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(url))
                            .GET()
                            .timeout(Duration.ofMillis(props.newsTimeoutMs()))
                            .header("Accept", "application/json")
                            .header("User-Agent", "tracker-server/1.0")
                            .build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.warn("Yahoo extended quote batch HTTP {}", resp.statusCode());
                return Map.of();
            }
            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode result = root.path("quoteResponse").path("result");
            if (!result.isArray() || result.isEmpty()) {
                return Map.of();
            }
            Map<String, YahooExtendedQuoteDto> out = new HashMap<>();
            for (JsonNode n : result) {
                if (n == null || n.isNull()) {
                    continue;
                }
                String sym = text(n, "symbol");
                if (sym.isEmpty()) {
                    continue;
                }
                Long vol3m = longNode(n, "averageDailyVolume3Month");
                if (vol3m == null) {
                    vol3m = longNode(n, "averageDailyVolume10Day");
                }
                out.put(
                        sym,
                        new YahooExtendedQuoteDto(
                                sym,
                                text(n, "shortName"),
                                text(n, "longName"),
                                dbl(n, "regularMarketPrice"),
                                dbl(n, "regularMarketChangePercent"),
                                longNode(n, "regularMarketVolume"),
                                vol3m,
                                dbl(n, "marketCap"),
                                dbl(n, "fiftyTwoWeekHigh"),
                                dbl(n, "fiftyTwoWeekLow"),
                                dbl(n, "trailingPE"),
                                text(n, "sector"),
                                text(n, "industry")));
            }
            return out;
        } catch (Exception e) {
            log.warn("Yahoo extended batch quote failed", e);
            return Map.of();
        }
    }

    private static Long longNode(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) {
            return null;
        }
        JsonNode v = n.get(field);
        if (v.isObject() && v.has("raw") && !v.get("raw").isNull()) {
            v = v.get("raw");
        }
        if (v.isIntegralNumber()) {
            return v.asLong();
        }
        if (v.isNumber()) {
            return Math.round(v.asDouble());
        }
        if (v.isTextual()) {
            String s = v.asText();
            if (s != null && !s.isBlank()) {
                try {
                    return Math.round(Double.parseDouble(s.trim().replace(",", "")));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) {
            return "";
        }
        if (v.isTextual()) {
            return v.asText("").trim();
        }
        return v.toString();
    }

    /** Yahoo often returns {@code {"raw":n,"fmt":"..."}} or text numbers; match screener-style parsing. */
    private static Double dbl(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) {
            return null;
        }
        JsonNode v = n.get(field);
        if (v.isObject() && v.has("raw") && !v.get("raw").isNull()) {
            v = v.get("raw");
        }
        if (v.isNumber()) {
            return v.asDouble();
        }
        if (v.isTextual()) {
            String s = v.asText();
            if (s != null && !s.isEmpty()) {
                try {
                    return Double.parseDouble(s.trim().replace(",", ""));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}
