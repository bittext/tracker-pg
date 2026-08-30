package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.finance.dto.CompanyFinancialsQuarterDto;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Alpha Vantage INCOME_STATEMENT + EARNINGS for the Markets → Research → Financials tab. Merges
 * both by fiscalDateEnding into up to 12 trailing quarters (~3 years). Cached briefly to stay
 * within free-tier rate limits, same pattern as {@link AlphaVantageOverviewService}.
 */
@Service
@Slf4j
public class AlphaVantageFinancialsService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(60);
    private static final int MAX_QUARTERS = 12;

    private final FinanceProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public AlphaVantageFinancialsService(FinanceProperties props) {
        this.props = props;
    }

    public record Result(List<CompanyFinancialsQuarterDto> quarters, List<String> warnings) {}

    public Result quarterlyFinancials(String symbolRaw) {
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
            return cached.result;
        }
        Result result = fetch(symbol, key);
        if (result != null) {
            cache.put(symbol, new CacheEntry(now, result));
        }
        return result;
    }

    private Result fetch(String symbol, String apiKey) {
        JsonNode income = callAlphaVantage("INCOME_STATEMENT", symbol, apiKey);
        JsonNode earnings = callAlphaVantage("EARNINGS", symbol, apiKey);
        if (income == null && earnings == null) {
            return null;
        }

        Map<String, double[]> incomeByQuarter = new LinkedHashMap<>();
        if (income != null) {
            JsonNode reports = income.path("quarterlyReports");
            if (reports.isArray()) {
                for (JsonNode r : reports) {
                    String fiscalDateEnding = text(r, "fiscalDateEnding");
                    if (fiscalDateEnding.isBlank()) {
                        continue;
                    }
                    incomeByQuarter.put(
                            fiscalDateEnding,
                            new double[] {
                                num(r, "totalRevenue"),
                                num(r, "netIncome"),
                                num(r, "grossProfit"),
                                num(r, "operatingIncome")
                            });
                }
            }
        }

        Map<String, double[]> epsByQuarter = new LinkedHashMap<>();
        if (earnings != null) {
            JsonNode reports = earnings.path("quarterlyEarnings");
            if (reports.isArray()) {
                for (JsonNode r : reports) {
                    String fiscalDateEnding = text(r, "fiscalDateEnding");
                    if (fiscalDateEnding.isBlank()) {
                        continue;
                    }
                    epsByQuarter.put(
                            fiscalDateEnding, new double[] {num(r, "reportedEPS"), num(r, "estimatedEPS")});
                }
            }
        }

        List<String> quarterKeys = new ArrayList<>(incomeByQuarter.keySet());
        for (String k : epsByQuarter.keySet()) {
            if (!incomeByQuarter.containsKey(k)) {
                quarterKeys.add(k);
            }
        }
        quarterKeys.sort((a, b) -> b.compareTo(a));
        if (quarterKeys.size() > MAX_QUARTERS) {
            quarterKeys = quarterKeys.subList(0, MAX_QUARTERS);
        }

        List<CompanyFinancialsQuarterDto> quarters = new ArrayList<>();
        for (String fiscalDateEnding : quarterKeys) {
            double[] inc = incomeByQuarter.get(fiscalDateEnding);
            double[] eps = epsByQuarter.get(fiscalDateEnding);
            Double revenue = inc != null ? nullIfNan(inc[0]) : null;
            Double netIncome = inc != null ? nullIfNan(inc[1]) : null;
            Double grossProfit = inc != null ? nullIfNan(inc[2]) : null;
            Double operatingIncome = inc != null ? nullIfNan(inc[3]) : null;
            Double netMarginPct =
                    (revenue != null && netIncome != null && revenue != 0) ? (netIncome / revenue) * 100 : null;
            Double epsActual = eps != null ? nullIfNan(eps[0]) : null;
            Double epsEstimate = eps != null ? nullIfNan(eps[1]) : null;
            Double epsSurprisePct = (epsActual != null && epsEstimate != null && epsEstimate != 0)
                    ? ((epsActual - epsEstimate) / Math.abs(epsEstimate)) * 100
                    : null;
            quarters.add(new CompanyFinancialsQuarterDto(
                    fiscalDateEnding,
                    revenue,
                    netIncome,
                    grossProfit,
                    operatingIncome,
                    netMarginPct,
                    epsActual,
                    epsEstimate,
                    epsSurprisePct));
        }
        // Oldest -> newest for trend computation and chronological display.
        quarters.sort((a, b) -> a.fiscalDateEnding().compareTo(b.fiscalDateEnding()));

        List<String> warnings = new ArrayList<>();
        if (quarters.size() < MAX_QUARTERS) {
            warnings.add("Only " + quarters.size() + " quarter(s) of history available.");
        }
        if (epsByQuarter.isEmpty()) {
            warnings.add("EPS estimate/actual data not available for this symbol.");
        }
        return new Result(quarters, warnings);
    }

    private JsonNode callAlphaVantage(String function, String symbol, String apiKey) {
        try {
            String url = props.alphaVantageBaseUrl()
                    + "?function="
                    + function
                    + "&symbol="
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
                log.warn("Alpha {} HTTP {} for {}", function, resp.statusCode(), symbol);
                return null;
            }
            JsonNode root = objectMapper.readTree(resp.body());
            if (!root.isObject() || root.has("Note") || root.has("Information") || root.has("Error Message")) {
                log.debug("Alpha {} unavailable for {} (rate limit or empty)", function, symbol);
                return null;
            }
            return root;
        } catch (Exception e) {
            log.warn("Alpha {} failed for {}: {}", function, symbol, e.toString());
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

    private static double num(JsonNode root, String field) {
        String v = text(root, field);
        if (v.isBlank() || "None".equalsIgnoreCase(v)) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static Double nullIfNan(double v) {
        return Double.isNaN(v) ? null : v;
    }

    private record CacheEntry(long atMs, Result result) {}
}
