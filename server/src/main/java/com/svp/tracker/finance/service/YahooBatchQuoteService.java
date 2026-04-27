package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.finance.dto.YahooExtendedQuoteDto;
import com.svp.tracker.finance.dto.YahooSimpleQuoteDto;
import com.svp.tracker.finance.repository.FinanceStockAlertRepository;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Fetches quote fields from Alpha Vantage. This class keeps its original name to avoid broad wiring changes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class YahooBatchQuoteService {

    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final List<String> ALWAYS_TRACKED =
            List.of("SPY", "QQQ", "DIA", "IWM", "HOOD", "CRWV", "NBIS");

    private final FinanceProperties props;
    private final FinanceStockAlertRepository alertRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Object refreshLock = new Object();

    private volatile Map<String, AlphaQuote> cache = Map.of();
    private volatile Instant lastRefreshAt = Instant.EPOCH;

    /**
     * Single hourly refresh cycle for all tracked symbols (alerts + fixed finance watch/index set).
     * Reads can still trigger a one-shot refresh if a requested symbol is missing from cache.
     */
    @Scheduled(
            fixedDelayString = "${tracker.finance.alpha-vantage-refresh-ms:3600000}",
            initialDelayString = "${tracker.finance.alpha-vantage-initial-delay-ms:45000}")
    public void refreshTrackedSymbolsHourly() {
        refreshAllSymbolsInOneShot(collectTrackedSymbols(), "hourly");
    }

    public Map<String, YahooSimpleQuoteDto> fetchBySymbols(List<String> symbols) {
        List<String> req = normalizeSymbols(symbols);
        if (req.isEmpty()) {
            return Map.of();
        }
        ensureFresh(req);
        Map<String, YahooSimpleQuoteDto> out = new HashMap<>();
        Map<String, AlphaQuote> snap = cache;
        for (String s : req) {
            AlphaQuote q = snap.get(s);
            if (q == null) {
                continue;
            }
            out.put(s, new YahooSimpleQuoteDto(s, s, q.price(), q.changePercent()));
        }
        return out;
    }

    /**
     * Same v7 batch endpoint, parsed for KPI / sector fields used in swing narratives. Missing fields stay null.
     */
    public Map<String, YahooExtendedQuoteDto> fetchExtendedBySymbols(List<String> symbols) {
        List<String> req = normalizeSymbols(symbols);
        if (req.isEmpty()) {
            return Map.of();
        }
        ensureFresh(req);
        Map<String, YahooExtendedQuoteDto> out = new HashMap<>();
        Map<String, AlphaQuote> snap = cache;
        for (String s : req) {
            AlphaQuote q = snap.get(s);
            if (q == null) {
                continue;
            }
            out.put(
                    s,
                    new YahooExtendedQuoteDto(
                            s,
                            s,
                            s,
                            q.price(),
                            q.changePercent(),
                            q.volume(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            "",
                            ""));
        }
        return out;
    }

    private void ensureFresh(List<String> requested) {
        Instant now = Instant.now();
        boolean stale = Duration.between(lastRefreshAt, now).compareTo(CACHE_TTL) >= 0;
        boolean missing = requested.stream().anyMatch(s -> !cache.containsKey(s));
        if (!stale && !missing) {
            return;
        }
        Set<String> union = new LinkedHashSet<>(collectTrackedSymbols());
        union.addAll(requested);
        refreshAllSymbolsInOneShot(union, stale ? "stale-cache" : "missing-symbols");
    }

    private Set<String> collectTrackedSymbols() {
        Set<String> out = new LinkedHashSet<>();
        out.addAll(ALWAYS_TRACKED);
        try {
            out.addAll(normalizeSymbols(alertRepository.findDistinctEnabledSymbols()));
        } catch (Exception e) {
            log.warn("Could not load enabled alert symbols for hourly Alpha refresh", e);
        }
        return out;
    }

    private void refreshAllSymbolsInOneShot(Set<String> symbols, String reason) {
        List<String> normalized = normalizeSymbols(symbols.stream().toList());
        if (normalized.isEmpty()) {
            return;
        }
        synchronized (refreshLock) {
            Instant now = Instant.now();
            // If another thread refreshed while we waited, keep fast-path cheap.
            boolean stillStale = Duration.between(lastRefreshAt, now).compareTo(CACHE_TTL) >= 0;
            boolean anyMissing = normalized.stream().anyMatch(s -> !cache.containsKey(s));
            if (!stillStale && !anyMissing && !"hourly".equals(reason)) {
                return;
            }
            Map<String, AlphaQuote> fresh = fetchAlphaBulkOneShot(normalized);
            if (fresh.isEmpty()) {
                return;
            }
            Map<String, AlphaQuote> merged = new HashMap<>(cache);
            for (Map.Entry<String, AlphaQuote> e : fresh.entrySet()) {
                AlphaQuote prev = merged.get(e.getKey());
                AlphaQuote next = e.getValue();
                if (prev != null && prev.price() != null && next.price() != null && prev.price() > 0) {
                    double pct = ((next.price() - prev.price()) / prev.price()) * 100.0;
                    next = new AlphaQuote(next.price(), pct, next.volume());
                }
                merged.put(e.getKey(), next);
            }
            cache = Map.copyOf(merged);
            lastRefreshAt = now;
            log.info(
                    "Alpha Vantage refresh reason={} requested={} received={}",
                    reason,
                    normalized.size(),
                    fresh.size());
        }
    }

    private Map<String, AlphaQuote> fetchAlphaBulkOneShot(List<String> symbols) {
        String key = props.alphaVantageApiKey();
        if (key == null || key.isBlank()) {
            log.warn("Alpha Vantage API key is not configured; set TRACKER_FINANCE_ALPHA_VANTAGE_API_KEY");
            return Map.of();
        }
        Map<String, AlphaQuote> out = new HashMap<>();
        for (String symbol : symbols) {
            String url = props.alphaVantageBaseUrl()
                    + "?function=GLOBAL_QUOTE&symbol="
                    + URLEncoder.encode(symbol, StandardCharsets.UTF_8)
                    + "&apikey="
                    + URLEncoder.encode(key, StandardCharsets.UTF_8);
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .GET()
                        .timeout(Duration.ofMillis(props.newsTimeoutMs()))
                        .header("Accept", "application/json")
                        .header("User-Agent", "tracker-server/1.0")
                        .build();
                HttpResponse<String> resp =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                    log.warn("Alpha Vantage quote HTTP {} for {}", resp.statusCode(), symbol);
                    continue;
                }
                JsonNode root = objectMapper.readTree(resp.body());
                if (root.has("Note")) {
                    log.warn("Alpha Vantage throttled quote requests: {}", root.path("Note").asText(""));
                    break;
                }
                if (root.has("Error Message")) {
                    log.warn("Alpha Vantage quote error for {}: {}", symbol, root.path("Error Message").asText(""));
                    continue;
                }
                JsonNode q = root.path("Global Quote");
                if (!q.isObject() || q.isEmpty()) {
                    continue;
                }
                Double price = toDouble(q.path("05. price").asText(null));
                Double pct = toPercentDouble(q.path("10. change percent").asText(null));
                Long volume = toLong(q.path("06. volume").asText(null));
                if (price == null && pct == null && volume == null) {
                    continue;
                }
                out.put(symbol, new AlphaQuote(price, pct, volume));
            } catch (Exception e) {
                log.warn("Alpha Vantage quote failed for {}", symbol, e);
            }
        }
        return out;
    }

    private static List<String> normalizeSymbols(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String s : symbols) {
            String sym = text(s).toUpperCase(Locale.ROOT);
            if (!sym.isEmpty()) {
                out.add(sym);
            }
        }
        return out.stream().toList();
    }

    private static String text(String s) {
        return s == null ? "" : s.trim();
    }

    private static Double toPercentDouble(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String cleaned = s.replace("%", "").trim();
        return toDouble(cleaned);
    }

    private static Double toDouble(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(s.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long toLong(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(s.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record AlphaQuote(Double price, Double changePercent, Long volume) {}
}
