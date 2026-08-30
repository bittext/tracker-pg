package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Robinhood marketdata earnings (same payload as Agentic {@code get_earnings_results}): up to eight
 * trailing quarters of EPS estimate/actual plus report date.
 */
@Service
@Slf4j
public class RobinhoodEarningsService {

    private static final String EARNINGS_URL = "https://api.robinhood.com/marketdata/earnings/?symbol=";
    private static final Duration CACHE_TTL = Duration.ofMinutes(45);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public record EarningsRow(
            int year, int quarter, LocalDate reportDate, Double epsActual, Double epsEstimate) {}

    public List<EarningsRow> earnings(String symbolRaw) {
        String symbol = symbolRaw == null ? "" : symbolRaw.trim().toUpperCase(Locale.ROOT);
        if (symbol.isBlank()) {
            return List.of();
        }
        CacheEntry cached = cache.get(symbol);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.atMs < CACHE_TTL.toMillis()) {
            return cached.rows;
        }
        List<EarningsRow> rows = fetch(symbol);
        if (!rows.isEmpty()) {
            cache.put(symbol, new CacheEntry(now, rows));
        }
        return rows;
    }

    private List<EarningsRow> fetch(String symbol) {
        try {
            String url = EARNINGS_URL + URLEncoder.encode(symbol, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(12))
                    .header("Accept", "application/json")
                    .header("User-Agent", "tracker-pg/company-financials")
                    .build();
            HttpResponse<String> resp =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.warn("Robinhood earnings HTTP {} for {}", resp.statusCode(), symbol);
                return List.of();
            }
            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode results = root.path("results");
            if (!results.isArray()) {
                return List.of();
            }
            List<EarningsRow> out = new ArrayList<>();
            for (JsonNode n : results) {
                JsonNode eps = n.path("eps");
                JsonNode report = n.path("report");
                LocalDate reportDate = parseDate(text(report, "date"));
                out.add(new EarningsRow(
                        n.path("year").asInt(0),
                        n.path("quarter").asInt(0),
                        reportDate,
                        parseNum(eps.path("actual").asText("")),
                        parseNum(eps.path("estimate").asText(""))));
            }
            return out;
        } catch (Exception e) {
            log.warn("Robinhood earnings failed for {}: {}", symbol, e.toString());
            return List.of();
        }
    }

    private static String text(JsonNode n, String field) {
        if (n == null || !n.isObject()) {
            return "";
        }
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? "" : v.asText("").trim();
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static Double parseNum(String raw) {
        if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) {
            return null;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record CacheEntry(long atMs, List<EarningsRow> rows) {}
}
