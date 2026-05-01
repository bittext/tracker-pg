package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.finance.dto.BreakoutCandidateRowDto;
import com.svp.tracker.finance.dto.BreakoutCandidatesDto;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Heuristic “positive breakout setup” scan: combines (1) price pressing or clearing a recent ~20-session resistance
 * band, (2) rising participation vs a prior volume baseline, (3) short-term volatility contraction vs a wider window,
 * and (4) price above medium-term trend (SMA50) when available. Uses Yahoo predefined screeners for a liquid universe
 * then enriches with daily chart OHLCV — same data family as {@link Surge52WeekHighsService}. Not investment advice;
 * many valid breakouts will not match and some matches will fail.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BreakoutScanService {

    private static final String SOURCE =
            "Yahoo screeners + 1y daily OHLCV (resistance proximity, volume vs 20d baseline, ATR contraction, SMA50 trend)";

    private static final String SCREENER_BASE =
            "https://query1.finance.yahoo.com/v1/finance/screener/predefined/saved"
                    + "?formatted=true&lang=en-US&region=US&count=%d&scrIds=%s";

    private static final String CHART_1Y =
            "https://query1.finance.yahoo.com/v8/finance/chart/%s"
                    + "?range=1y&interval=1d&includeAdjustedClose=true";

    /** Need SMA50 + buffers for resistance and ATR windows. */
    private static final int MIN_BARS = 72;

    private static final int RESISTANCE_LOOKBACK = 20;
    /** Bars to skip at the end when forming “prior” resistance (avoid same-day noise). */
    private static final int RESISTANCE_LAG = 2;

    private final FinanceProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BreakoutCandidatesDto scan(Integer limitRaw) {
        int limit = sanitizeLimit(limitRaw);
        int perScreener = 100;
        int maxUnion = Math.min(320, Math.max(160, limit * 12));
        int perListCap = 80;

        JsonNode dayGainers = fetchScreenerJson("day_gainers", perScreener);
        JsonNode mostActives = fetchScreenerJsonOptional("most_actives", perScreener);
        JsonNode aggressive = fetchScreenerJsonOptional("aggressive_small_gainers", perScreener);
        JsonNode undervaluedGrowth = fetchScreenerJsonOptional("undervalued_growth_stocks", 80);
        JsonNode smallCapGainers = fetchScreenerJsonOptional("small_cap_gainers", 80);

        JsonNode[] roots = {dayGainers, mostActives, aggressive, undervaluedGrowth, smallCapGainers};
        Map<String, JsonNode> quoteBySymbol = mergeQuotesOrdered(roots);
        List<String> symbols = unionSymbolsFair(perListCap, maxUnion, roots);

        List<BreakoutCandidateRowDto> scored = scoreInParallel(symbols, quoteBySymbol);

        scored.sort(Comparator.comparingDouble(BreakoutCandidateRowDto::breakoutScore).reversed());
        if (scored.size() > limit) {
            scored = new ArrayList<>(scored.subList(0, limit));
        }

        String note =
                "Pattern idea: breakouts often follow a period where price tightens under a recent high (volatility "
                        + "contracts), volume firms on tests of that level, and then price clears the shelf — or lifts "
                        + "with volume while still just below it (coiled). This scan scores those ingredients from "
                        + "daily adjusted closes, highs/lows, volume, and session quote vs a ~20-session prior ceiling "
                        + "(excluding the last couple sessions). Rows need a minimum composite score and at least one "
                        + "strong pressure or flow signal. Data can be delayed; verify any name independently.";

        return new BreakoutCandidatesDto(SOURCE, Instant.now().toString(), scored.size(), note, scored);
    }

    private int sanitizeLimit(Integer raw) {
        int cap = Math.min(35, props.newsMaxItems() > 0 ? props.newsMaxItems() : 25);
        if (raw == null) {
            return cap;
        }
        if (raw < 1) {
            return 1;
        }
        return Math.min(raw, cap);
    }

    private List<BreakoutCandidateRowDto> scoreInParallel(List<String> symbols, Map<String, JsonNode> quoteBySymbol) {
        if (symbols.isEmpty()) {
            return List.of();
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<BreakoutCandidateRowDto>> futures = new ArrayList<>(symbols.size());
            for (String sym : symbols) {
                futures.add(
                        pool.submit(
                                () -> {
                                    JsonNode q = quoteBySymbol.get(sym.toUpperCase(Locale.ROOT));
                                    return buildOne(sym, q);
                                }));
            }
            List<BreakoutCandidateRowDto> out = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                try {
                    BreakoutCandidateRowDto row = futures.get(i).get();
                    if (row != null) {
                        out.add(row);
                    }
                } catch (ExecutionException e) {
                    log.debug("breakout task failed for {}", symbols.get(i), e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while scanning breakouts", e);
                }
            }
            return out;
        } finally {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(180, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private BreakoutCandidateRowDto buildOne(String symbol, JsonNode quote) {
        JsonNode chart;
        try {
            chart = fetchChart1y(symbol);
        } catch (Exception e) {
            log.debug("breakout chart fetch failed for {}", symbol, e);
            return null;
        }
        double[] close = extractAdjClose(chart);
        double[] high = extractQuoteSeries(chart, "high");
        double[] low = extractQuoteSeries(chart, "low");
        double[] vol = extractQuoteSeries(chart, "volume");
        int n = close.length;
        if (n < MIN_BARS || high.length != n || low.length != n || vol.length != n) {
            return null;
        }

        double[] tr = trueRanges(high, low, close, n);
        int last = n - 1;
        double lastClose = close[last];

        double price = lastClose;
        Double sessionChg = null;
        Double pct52 = null;
        Double high52 = null;
        String name = symbol;
        if (quote != null && !quote.isNull()) {
            Double qpx = dbl(quote, "regularMarketPrice");
            if (qpx != null && qpx > 0) {
                price = qpx;
            }
            sessionChg = dbl(quote, "regularMarketChangePercent");
            high52 = dbl(quote, "fiftyTwoWeekHigh");
            if (qpx != null && high52 != null && high52 > 0) {
                pct52 = round2(100.0 * qpx / high52);
            }
            name = text(quote, "shortName");
            if (name == null || name.isBlank()) {
                name = text(quote, "longName");
            }
            if (name == null || name.isBlank()) {
                name = symbol;
            }
        }

        int resFrom = last - RESISTANCE_LOOKBACK - RESISTANCE_LAG + 1;
        int resTo = last - RESISTANCE_LAG + 1;
        if (resFrom < 0) {
            return null;
        }
        double resHigh = maxClose(close, resFrom, resTo);
        if (resHigh <= 0) {
            return null;
        }
        double pctNearRes = round2(100.0 * price / resHigh);

        double volRatio = volumeRatio(vol, last);
        Double volRatioObj = volRatio > 0 ? round2(volRatio) : null;

        Double atrRatio = atrCompressionRatio(tr, last);
        double sma20 = sma(close, last, 20);
        double sma50 = sma(close, last, 50);
        Double pctVs50 = null;
        if (sma50 > 0) {
            pctVs50 = round2(100.0 * (price / sma50 - 1.0));
        }

        double resPts = resistancePoints(pctNearRes);
        double volPts = volumePoints(volRatio);
        double sqzPts = squeezePoints(atrRatio);
        double trdPts = trendPoints(price, sma20, sma50);
        double raw = resPts + volPts + sqzPts + trdPts;
        double score = round2(Math.min(100.0, raw));

        boolean passMin =
                score >= 46.0
                        && (pctNearRes >= 97.8 || (volRatioObj != null && volRatioObj >= 1.18) || (atrRatio != null && atrRatio <= 0.9 && pctNearRes >= 96.5));
        if (!passMin) {
            return null;
        }

        String pattern = patternLabel(pctNearRes, volRatio, atrRatio, price, sma50);
        String rationale =
                buildRationale(pctNearRes, volRatioObj, atrRatio, pctVs50, sessionChg, sma20, sma50, price, resHigh);

        return new BreakoutCandidateRowDto(
                symbol.trim(),
                name,
                round2(price),
                sessionChg != null ? round2(sessionChg) : null,
                pct52,
                score,
                pattern,
                rationale,
                round2(pctNearRes),
                volRatioObj,
                atrRatio != null ? round2(atrRatio) : null,
                pctVs50,
                externalQuoteUrl(symbol));
    }

    private static String patternLabel(
            double pctNearRes, double volRatio, Double atrRatio, double price, double sma50) {
        if (pctNearRes >= 100.2 && volRatio >= 1.12) {
            return "Break + volume";
        }
        if (pctNearRes >= 99.5 && volRatio >= 1.2) {
            return "Test with flow";
        }
        if (atrRatio != null && atrRatio <= 0.9 && pctNearRes >= 98.0) {
            return "Squeeze near shelf";
        }
        if (price > sma50 && pctNearRes >= 99.0) {
            return "Trend + resistance press";
        }
        if (pctNearRes >= 100.0) {
            return "Price clearing prior ceiling";
        }
        return "Coiled setup";
    }

    private static String buildRationale(
            double pctNearRes,
            Double volRatio,
            Double atrRatio,
            Double pctVs50,
            Double sessionChg,
            double sma20,
            double sma50,
            double price,
            double resHigh) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "Price (~%.2f) vs ~20d prior high (~%.2f): %.1f%%. ", price, resHigh, pctNearRes));
        if (volRatio != null) {
            sb.append(String.format(Locale.US, "5d/20d volume ratio ~%.2f. ", volRatio));
        }
        if (atrRatio != null) {
            sb.append(String.format(Locale.US, "ATR(10)/ATR(11–30) ~%.2f (%s). ", atrRatio, atrRatio < 1.0 ? "contracted" : "neutral"));
        }
        if (pctVs50 != null) {
            sb.append(String.format(Locale.US, "~%.1f%% vs 50d SMA. ", pctVs50));
        }
        if (sessionChg != null) {
            sb.append(String.format(Locale.US, "Session %% change ~%.2f%%. ", sessionChg));
        }
        if (sma20 > sma50) {
            sb.append("20d SMA above 50d SMA (short trend aligned). ");
        }
        sb.append("Heuristic only.");
        return sb.toString();
    }

    private static double resistancePoints(double pctNearRes) {
        if (pctNearRes < 96.0) {
            return 0.0;
        }
        return Math.min(42.0, 42.0 * (pctNearRes - 96.0) / 6.0);
    }

    private static double volumePoints(double volRatio) {
        if (volRatio <= 1.0) {
            return 0.0;
        }
        return Math.min(34.0, 34.0 * (volRatio - 1.0) / 0.65);
    }

    private static double squeezePoints(Double atrRatio) {
        if (atrRatio == null) {
            return 0.0;
        }
        if (atrRatio <= 0.82) {
            return 16.0;
        }
        if (atrRatio <= 0.92) {
            return 12.0;
        }
        if (atrRatio <= 1.0) {
            return 6.0;
        }
        return 0.0;
    }

    private static double trendPoints(double price, double sma20, double sma50) {
        if (sma50 <= 0) {
            return 0.0;
        }
        if (price > sma50 && sma20 > sma50) {
            return 14.0;
        }
        if (price > sma50) {
            return 9.0;
        }
        if (price > sma20) {
            return 4.0;
        }
        return 0.0;
    }

    private static double maxClose(double[] close, int from, int toExclusive) {
        double m = Double.NEGATIVE_INFINITY;
        for (int i = from; i < toExclusive; i++) {
            if (close[i] > m) {
                m = close[i];
            }
        }
        return m;
    }

    private static double volumeRatio(double[] vol, int last) {
        double a = avg(vol, last - 4, last + 1);
        double b = avg(vol, last - 24, last - 4);
        if (b <= 0 || a <= 0) {
            return 0.0;
        }
        return a / b;
    }

    private static double avg(double[] a, int from, int toExclusive) {
        double s = 0.0;
        int c = 0;
        for (int i = from; i < toExclusive; i++) {
            if (i >= 0 && i < a.length && a[i] > 0) {
                s += a[i];
                c++;
            }
        }
        return c > 0 ? s / c : 0.0;
    }

    private static Double atrCompressionRatio(double[] tr, int last) {
        if (last < 30) {
            return null;
        }
        double m10 = avgTr(tr, last - 9, last + 1);
        double m20 = avgTr(tr, last - 29, last - 9);
        if (m20 <= 0 || m10 <= 0) {
            return null;
        }
        return m10 / m20;
    }

    private static double avgTr(double[] tr, int from, int toExclusive) {
        double s = 0.0;
        int c = 0;
        for (int i = from; i < toExclusive; i++) {
            if (i >= 0 && i < tr.length && tr[i] > 0) {
                s += tr[i];
                c++;
            }
        }
        return c > 0 ? s / c : 0.0;
    }

    private static double[] trueRanges(double[] high, double[] low, double[] close, int n) {
        double[] tr = new double[n];
        for (int i = 0; i < n; i++) {
            if (i == 0) {
                tr[i] = Math.max(0.0, high[i] - low[i]);
            } else {
                double hl = high[i] - low[i];
                double hc = Math.abs(high[i] - close[i - 1]);
                double lc = Math.abs(low[i] - close[i - 1]);
                tr[i] = Math.max(hl, Math.max(hc, lc));
            }
        }
        return tr;
    }

    private static double sma(double[] close, int last, int window) {
        if (last - window + 1 < 0) {
            return 0.0;
        }
        double s = 0.0;
        for (int i = last - window + 1; i <= last; i++) {
            s += close[i];
        }
        return s / window;
    }

    private JsonNode fetchChart1y(String symbol) throws Exception {
        String enc = URLEncoder.encode(symbol, StandardCharsets.UTF_8).replace("+", "%20");
        String url = String.format(Locale.ROOT, CHART_1Y, enc);
        HttpClient client =
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(props.newsTimeoutMs())).build();
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(url))
                        .GET()
                        .timeout(Duration.ofMillis(props.newsTimeoutMs()))
                        .header("Accept", "application/json")
                        .header("User-Agent", "tracker-server/1.0")
                        .build();
        HttpResponse<String> resp =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException("chart HTTP " + resp.statusCode());
        }
        return objectMapper.readTree(resp.body());
    }

    private JsonNode fetchScreenerJson(String scrId, int count) {
        try {
            String url = String.format(Locale.ROOT, SCREENER_BASE, count, scrId);
            HttpClient client =
                    HttpClient.newBuilder().connectTimeout(Duration.ofMillis(props.newsTimeoutMs())).build();
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(url))
                            .GET()
                            .timeout(Duration.ofMillis(props.newsTimeoutMs()))
                            .header("Accept", "application/json")
                            .header("User-Agent", "tracker-server/1.0")
                            .build();
            HttpResponse<String> resp =
                    client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new IllegalStateException("screener " + scrId + " HTTP " + resp.statusCode());
            }
            return objectMapper.readTree(resp.body());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("breakout screener fetch failed: {}", scrId, e);
            throw new IllegalStateException("Could not load screener " + scrId, e);
        }
    }

    private JsonNode fetchScreenerJsonOptional(String scrId, int count) {
        try {
            return fetchScreenerJson(scrId, count);
        } catch (Exception e) {
            log.debug("optional breakout screener {} skipped: {}", scrId, e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private static Map<String, JsonNode> mergeQuotesOrdered(JsonNode[] roots) {
        Map<String, JsonNode> map = new HashMap<>();
        for (JsonNode root : roots) {
            if (root != null) {
                appendQuotes(root, map, true);
            }
        }
        return map;
    }

    private static List<String> unionSymbolsFair(int maxIterationsPerScreener, int maxTotalSymbols, JsonNode[] roots) {
        LinkedHashSet<String> order = new LinkedHashSet<>();
        for (JsonNode root : roots) {
            if (root == null) {
                continue;
            }
            if (order.size() >= maxTotalSymbols) {
                break;
            }
            addSymbolsUpTo(root, order, maxIterationsPerScreener, maxTotalSymbols);
        }
        return new ArrayList<>(order);
    }

    private static void addSymbolsUpTo(
            JsonNode root, LinkedHashSet<String> order, int maxIterationsFromRoot, int maxTotal) {
        JsonNode quotes = firstResultQuotes(root);
        if (quotes == null || !quotes.isArray()) {
            return;
        }
        int n = 0;
        for (JsonNode q : quotes) {
            if (order.size() >= maxTotal) {
                return;
            }
            if (n >= maxIterationsFromRoot) {
                return;
            }
            n++;
            String sym = text(q, "symbol");
            if (sym != null && !sym.isBlank()) {
                order.add(sym.trim());
            }
        }
    }

    private static void appendQuotes(JsonNode root, Map<String, JsonNode> map, boolean onlyIfAbsent) {
        JsonNode quotes = firstResultQuotes(root);
        if (quotes == null || !quotes.isArray()) {
            return;
        }
        for (JsonNode q : quotes) {
            String sym = text(q, "symbol");
            if (sym == null || sym.isBlank()) {
                continue;
            }
            String key = sym.trim().toUpperCase(Locale.ROOT);
            if (!onlyIfAbsent || !map.containsKey(key)) {
                map.put(key, q);
            }
        }
    }

    private static JsonNode firstResultQuotes(JsonNode root) {
        if (root == null) {
            return null;
        }
        JsonNode finance = root.get("finance");
        if (finance == null) {
            return null;
        }
        JsonNode result = finance.get("result");
        if (result == null || !result.isArray() || result.size() == 0) {
            return null;
        }
        JsonNode first = result.get(0);
        return first != null ? first.get("quotes") : null;
    }

    private static double[] extractAdjClose(JsonNode root) {
        JsonNode result = root.path("chart").path("result");
        if (!result.isArray() || result.isEmpty()) {
            return new double[0];
        }
        JsonNode r = result.get(0);
        JsonNode priceSeries = null;
        JsonNode adj = r.path("indicators").path("adjclose");
        if (adj.isArray() && !adj.isEmpty() && adj.get(0) != null) {
            priceSeries = adj.get(0).get("adjclose");
        }
        if (priceSeries == null || !priceSeries.isArray()) {
            JsonNode quote = r.path("indicators").path("quote");
            if (quote.isArray() && !quote.isEmpty() && quote.get(0) != null) {
                priceSeries = quote.get(0).get("close");
            }
        }
        return forwardFillPositivePrice(priceSeries);
    }

    private static double[] extractQuoteSeries(JsonNode root, String field) {
        JsonNode result = root.path("chart").path("result");
        if (!result.isArray() || result.isEmpty()) {
            return new double[0];
        }
        JsonNode r = result.get(0);
        JsonNode quote = r.path("indicators").path("quote");
        if (!quote.isArray() || quote.isEmpty() || quote.get(0) == null) {
            return new double[0];
        }
        JsonNode series = quote.get(0).get(field);
        return "volume".equals(field) ? forwardFillVolume(series) : forwardFillPositivePrice(series);
    }

    /** Forward-filled positive prices (close/high/low). */
    private static double[] forwardFillPositivePrice(JsonNode priceSeries) {
        if (priceSeries == null || !priceSeries.isArray()) {
            return new double[0];
        }
        int n = priceSeries.size();
        double[] raw = new double[n];
        boolean any = false;
        for (int i = 0; i < n; i++) {
            JsonNode px = priceSeries.get(i);
            if (px != null && !px.isNull() && px.isNumber()) {
                double v = px.asDouble();
                if (v > 0) {
                    raw[i] = v;
                    any = true;
                } else {
                    raw[i] = Double.NaN;
                }
            } else {
                raw[i] = Double.NaN;
            }
        }
        if (!any) {
            return new double[0];
        }
        double last = Double.NaN;
        for (int i = 0; i < n; i++) {
            if (Double.isNaN(raw[i])) {
                raw[i] = last;
            } else {
                last = raw[i];
            }
        }
        double next = Double.NaN;
        for (int i = n - 1; i >= 0; i--) {
            if (!Double.isNaN(raw[i]) && raw[i] > 0) {
                next = raw[i];
            } else if (!Double.isNaN(next)) {
                raw[i] = next;
            }
        }
        return raw;
    }

    /** Forward-filled non-negative volume; missing bars become last known or zero. */
    private static double[] forwardFillVolume(JsonNode volSeries) {
        if (volSeries == null || !volSeries.isArray()) {
            return new double[0];
        }
        int n = volSeries.size();
        double[] raw = new double[n];
        for (int i = 0; i < n; i++) {
            JsonNode px = volSeries.get(i);
            if (px != null && !px.isNull() && px.isNumber()) {
                double v = px.asDouble();
                raw[i] = v >= 0 ? v : Double.NaN;
            } else {
                raw[i] = Double.NaN;
            }
        }
        double last = 0.0;
        boolean any = false;
        for (int i = 0; i < n; i++) {
            if (Double.isNaN(raw[i])) {
                raw[i] = last;
            } else {
                last = raw[i];
                any = true;
            }
        }
        if (!any) {
            return new double[0];
        }
        return raw;
    }

    private static String externalQuoteUrl(String symbol) {
        String s = symbol.trim().replace("^", "%5E");
        return "https://finance.yahoo.com/quote/" + s;
    }

    private static String text(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) {
            return null;
        }
        String s = n.get(field).asText();
        return s == null ? null : s.trim();
    }

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
            if (s != null) {
                s = s.trim();
                if (!s.isEmpty()) {
                    try {
                        return Double.parseDouble(s);
                    } catch (Exception ignored) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
