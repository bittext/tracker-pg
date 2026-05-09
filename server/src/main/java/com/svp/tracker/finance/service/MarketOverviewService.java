package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.finance.dto.MarketOverviewDto;
import com.svp.tracker.finance.dto.MarketOverviewInstrumentDto;
import com.svp.tracker.finance.dto.MarketOverviewSectionDto;
import com.svp.tracker.finance.dto.MarketOverviewSummaryDto;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Finance → Market tab: Yahoo Finance v8/chart only (v7/quote is often HTTP 401 for server clients). Each symbol loads
 * one 2y daily chart: meta supplies session price and day % vs previous close; the same series drives MTD/YTD. Not
 * investment advice.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketOverviewService {

    private static final String SOURCE =
            "Yahoo Finance v8/chart (2y daily): day % from latest vs prior daily adjusted close; last price from quote meta "
                    + "when available else last bar close; MTD/YTD from same series.";

    /** Yahoo frequently returns 401 for programmatic clients unless the UA looks like a normal browser. */
    private static final String YAHOO_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final ZoneId NY = ZoneId.of("America/New_York");
    private static final String CHART_2Y =
            "https://query1.finance.yahoo.com/v8/finance/chart/%s"
                    + "?range=2y&interval=1d&includeAdjustedClose=true";

    private static final List<SectionDef> SECTIONS =
            List.of(
                    new SectionDef(
                            "US equity futures",
                            "Overnight / pre-market contracts (continuous).",
                            List.of(
                                    new RowDef("ES=F", "S&P 500 futures"),
                                    new RowDef("NQ=F", "Nasdaq 100 futures"),
                                    new RowDef("YM=F", "Dow futures"),
                                    new RowDef("RTY=F", "Russell 2000 futures"))),
                    new SectionDef(
                            "US exchange composites",
                            "Broad venue-level composites (not single stocks).",
                            List.of(
                                    new RowDef("^NYA", "NYSE Composite"),
                                    new RowDef("^IXIC", "Nasdaq Composite"),
                                    new RowDef("^RUT", "Russell 2000"),
                                    new RowDef("^W5000", "Wilshire 5000"))),
                    new SectionDef(
                            "US headline indexes",
                            "Benchmark levels most quoted for US equities.",
                            List.of(
                                    new RowDef("^GSPC", "S&P 500"),
                                    new RowDef("^DJI", "Dow Jones Industrial Average"),
                                    new RowDef("^IXIC", "Nasdaq Composite"),
                                    new RowDef("^RUT", "Russell 2000"),
                                    new RowDef("^VIX", "CBOE Volatility Index"))),
                    new SectionDef(
                            "Global indexes",
                            "Major non-US cash indexes (incl. India NSE/BSE benchmarks) and a broad emerging-markets ETF.",
                            List.of(
                                    new RowDef("^N225", "Nikkei 225"),
                                    new RowDef("^HSI", "Hang Seng"),
                                    new RowDef("^NSEI", "Nifty 50 (NSE)"),
                                    new RowDef("^BSESN", "S&P BSE Sensex"),
                                    new RowDef("^FTSE", "FTSE 100"),
                                    new RowDef("^GDAXI", "DAX"),
                                    new RowDef("^FCHI", "CAC 40"),
                                    new RowDef("^STOXX50E", "EURO STOXX 50"),
                                    new RowDef("EEM", "iShares MSCI Emerging Markets ETF"))));

    private final FinanceProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MarketOverviewDto load() {
        Instant started = Instant.now();
        List<String> warnings = Collections.synchronizedList(new ArrayList<>());
        Set<String> allSyms = new LinkedHashSet<>();
        for (SectionDef s : SECTIONS) {
            for (RowDef r : s.rows()) {
                allSyms.add(r.symbol().toUpperCase(Locale.ROOT));
            }
        }
        List<String> symbolList = new ArrayList<>(allSyms);
        Map<String, QuoteRow> quotes = new LinkedHashMap<>();
        Map<String, PeriodReturns> periods = new LinkedHashMap<>();
        fetchChartsParallel(symbolList, quotes, periods, warnings);

        List<MarketOverviewSectionDto> sections = new ArrayList<>();
        for (SectionDef s : SECTIONS) {
            List<MarketOverviewInstrumentDto> rows = new ArrayList<>();
            for (RowDef r : s.rows()) {
                String key = r.symbol().toUpperCase(Locale.ROOT);
                QuoteRow q = quotes.get(key);
                PeriodReturns pr = periods.get(key);
                Double day = q != null ? q.changePctDay() : null;
                Double mtd = pr != null ? pr.mtdPct() : null;
                Double ytd = pr != null ? pr.ytdPct() : null;
                Double px = q != null ? q.price() : null;
                String name = pickName(r, q);
                rows.add(new MarketOverviewInstrumentDto(
                        r.symbol(),
                        name,
                        px,
                        day,
                        mtd,
                        ytd,
                        yahooQuoteUrl(r.symbol())));
            }
            sections.add(new MarketOverviewSectionDto(s.title(), s.subtitle(), List.copyOf(rows)));
        }

        MarketOverviewSummaryDto summary = buildSummary(quotes, warnings);
        String note =
                "Day % for cash indexes and ETFs uses the latest two daily closes in the series (session-to-session); "
                        + "continuous futures prefer Yahoo meta first because daily adjusted closes can jump on rolls. "
                        + "MTD / YTD use the first US trading session close of the calendar month and year vs the latest "
                        + "daily close (non-US markets may look slightly off vs local calendars).";

        return new MarketOverviewDto(
                SOURCE,
                started.toString(),
                note,
                List.copyOf(warnings),
                summary,
                List.copyOf(sections));
    }

    private void fetchChartsParallel(
            List<String> symbols,
            Map<String, QuoteRow> quotesOut,
            Map<String, PeriodReturns> periodsOut,
            List<String> warnings) {
        if (symbols.isEmpty()) {
            return;
        }
        int timeoutMs = Math.max(props.newsTimeoutMs(), 25_000);
        try (ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor()) {
            Map<String, CompletableFuture<Void>> futs = new LinkedHashMap<>();
            for (String sym : symbols) {
                futs.put(
                        sym,
                        CompletableFuture.runAsync(
                                () -> {
                                    try {
                                        JsonNode root = fetchChart(sym, timeoutMs);
                                        JsonNode err = root.path("chart").path("error");
                                        if (!err.isMissingNode() && err.isObject() && !err.isEmpty()) {
                                            warnings.add(sym + ": Yahoo chart error " + err.toString());
                                            return;
                                        }
                                        QuoteRow q = parseQuoteFromChart(root, sym);
                                        PeriodReturns pr = computeMtdYtd(root);
                                        synchronized (quotesOut) {
                                            if (q != null) {
                                                quotesOut.put(sym, q);
                                            }
                                            if (pr != null) {
                                                periodsOut.put(sym, pr);
                                            }
                                        }
                                    } catch (Exception e) {
                                        log.warn("Yahoo chart failed for {}", sym, e);
                                        warnings.add("Chart failed for " + sym + ": " + e.getMessage());
                                    }
                                },
                                ex));
            }
            for (Map.Entry<String, CompletableFuture<Void>> e : futs.entrySet()) {
                try {
                    e.getValue().join();
                } catch (Exception exn) {
                    log.warn("chart task join failed for {}", e.getKey(), exn);
                    warnings.add("Chart task failed for " + e.getKey() + ".");
                }
            }
        }
    }

    private static MarketOverviewSummaryDto buildSummary(Map<String, QuoteRow> quotes, List<String> warnings) {
        QuoteRow vix = quotes.get("^VIX");
        QuoteRow spx = quotes.get("^GSPC");
        QuoteRow ixic = quotes.get("^IXIC");
        QuoteRow dji = quotes.get("^DJI");
        QuoteRow rut = quotes.get("^RUT");

        Double vixPx = vix != null ? vix.price() : null;
        Double vixChg = vix != null ? vix.changePctDay() : null;
        Double sp = spx != null ? spx.changePctDay() : null;
        Double nd = ixic != null ? ixic.changePctDay() : null;
        Double dj = dji != null ? dji.changePctDay() : null;
        Double ru = rut != null ? rut.changePctDay() : null;

        int n = 0;
        double sum = 0.0;
        // Arrays.asList allows null elements; List.of rejects null (NPE).
        for (Double x : Arrays.asList(sp, nd, dj, ru)) {
            if (x != null && !Double.isNaN(x)) {
                sum += x;
                n++;
            }
        }
        Double avg = n > 0 ? sum / n : null;

        String narrative;
        if (avg == null) {
            narrative = "US headline index session moves were unavailable in this refresh.";
            warnings.add("Summary: missing one or more of ^GSPC, ^IXIC, ^DJI, ^RUT quotes.");
        } else {
            String tone = avg > 0.15 ? "firm" : avg < -0.15 ? "soft" : "mixed / flat";
            narrative = String.format(
                    Locale.US,
                    "US large-cap tone looks %s: average day move across S&P 500, Nasdaq Composite, Dow, and Russell "
                            + "2000 is %+,.2f%% (indicative only).",
                    tone,
                    avg);
            if (vixChg != null && vixPx != null) {
                narrative +=
                        String.format(Locale.US, " VIX at %.2f (%+.2f%%).", vixPx, vixChg);
            }
        }

        return new MarketOverviewSummaryDto(narrative, vixPx, vixChg, sp, nd, dj, ru);
    }

    private static String pickName(RowDef r, QuoteRow q) {
        if (r.label() != null && !r.label().isBlank()) {
            return r.label();
        }
        if (q != null && q.shortName() != null && !q.shortName().isBlank()) {
            return q.shortName();
        }
        if (q != null && q.longName() != null && !q.longName().isBlank()) {
            return q.longName();
        }
        return r.symbol();
    }

    private JsonNode fetchChart(String symbol, int timeoutMs) throws Exception {
        String enc = URLEncoder.encode(symbol, StandardCharsets.UTF_8).replace("+", "%20");
        String url = String.format(Locale.ROOT, CHART_2Y, enc);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Accept", "application/json")
                .header("User-Agent", YAHOO_USER_AGENT)
                .build();
        HttpResponse<String> resp =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + resp.statusCode());
        }
        return objectMapper.readTree(resp.body());
    }

    private static QuoteRow parseQuoteFromChart(JsonNode root, String requestedSymbol) {
        JsonNode result = root.path("chart").path("result");
        if (!result.isArray() || result.isEmpty()) {
            return null;
        }
        JsonNode r0 = result.get(0);
        JsonNode meta = r0.path("meta");
        String sym = meta.path("symbol").asText("").trim();
        if (sym.isEmpty()) {
            sym = requestedSymbol;
        }
        String key = sym.toUpperCase(Locale.ROOT);
        Double price = dbl(meta.get("regularMarketPrice"));
        if (price == null || price <= 0.0) {
            price = lastValidCloseFromSeries(r0);
        }
        boolean futuresContract = requestedSymbol.contains("=");
        Double chg =
                futuresContract ? dayChangePctFromMeta(meta) : dayChangePctFromDailySeries(r0);
        if (chg == null) {
            chg = futuresContract ? dayChangePctFromDailySeries(r0) : dayChangePctFromMeta(meta);
        }
        String sn = text(meta.get("shortName"));
        String ln = text(meta.get("longName"));
        return new QuoteRow(key, sn, ln, price, chg);
    }

    /** Session move from last vs prior daily bar (matches how MTD/YTD use the same close series). */
    private static Double dayChangePctFromDailySeries(JsonNode resultNode) {
        double[] closes = extractAdjCloseSeries(resultNode);
        if (closes.length < 2) {
            return null;
        }
        int i = closes.length - 1;
        while (i >= 0 && (closes[i] <= 0 || Double.isNaN(closes[i]))) {
            i--;
        }
        if (i <= 0) {
            return null;
        }
        int j = i - 1;
        while (j >= 0 && (closes[j] <= 0 || Double.isNaN(closes[j]))) {
            j--;
        }
        if (j < 0) {
            return null;
        }
        double last = closes[i];
        double prev = closes[j];
        if (prev <= 0.0) {
            return null;
        }
        return 100.0 * (last / prev - 1.0);
    }

    private static Double lastValidCloseFromSeries(JsonNode resultNode) {
        double[] closes = extractAdjCloseSeries(resultNode);
        for (int i = closes.length - 1; i >= 0; i--) {
            if (closes[i] > 0 && !Double.isNaN(closes[i])) {
                return closes[i];
            }
        }
        return null;
    }

    private static Double dayChangePctFromMeta(JsonNode meta) {
        Double pct = dbl(meta.get("regularMarketChangePercent"));
        if (pct != null && !Double.isNaN(pct)) {
            return pct;
        }
        Double price = dbl(meta.get("regularMarketPrice"));
        Double prev = dbl(meta.get("previousClose"));
        if (prev == null) {
            prev = dbl(meta.get("chartPreviousClose"));
        }
        if (price != null && prev != null && prev > 0.0) {
            return (price / prev - 1.0) * 100.0;
        }
        return null;
    }

    private static PeriodReturns computeMtdYtd(JsonNode root) {
        JsonNode result = root.path("chart").path("result");
        if (!result.isArray() || result.isEmpty()) {
            return null;
        }
        JsonNode r = result.get(0);
        JsonNode ts = r.get("timestamp");
        if (ts == null || !ts.isArray() || ts.isEmpty()) {
            return null;
        }
        int n = ts.size();
        long[] times = new long[n];
        for (int i = 0; i < n; i++) {
            JsonNode t = ts.get(i);
            times[i] = t != null && t.isNumber() ? t.asLong() : 0L;
        }
        double[] closes = extractAdjCloseSeries(r);
        if (closes.length != n || n < 2) {
            return null;
        }
        ZonedDateTime nowNy = ZonedDateTime.now(NY);
        int y = nowNy.getYear();
        int m = nowNy.getMonthValue();

        Integer firstYtdIdx = null;
        Integer firstMtdIdx = null;
        for (int i = 0; i < n; i++) {
            if (times[i] <= 0) {
                continue;
            }
            LocalDate d = Instant.ofEpochSecond(times[i]).atZone(NY).toLocalDate();
            if (d.getYear() == y && firstYtdIdx == null) {
                firstYtdIdx = i;
            }
            if (d.getYear() == y && d.getMonthValue() == m && firstMtdIdx == null) {
                firstMtdIdx = i;
            }
        }
        if (firstYtdIdx == null || firstMtdIdx == null) {
            return null;
        }
        int last = n - 1;
        while (last > 0 && (closes[last] <= 0 || Double.isNaN(closes[last]))) {
            last--;
        }
        double lastPx = closes[last];
        if (lastPx <= 0) {
            return null;
        }
        double ytdBase = closes[firstYtdIdx];
        double mtdBase = closes[firstMtdIdx];
        if (ytdBase <= 0 || mtdBase <= 0) {
            return null;
        }
        Double ytd = 100.0 * (lastPx / ytdBase - 1.0);
        Double mtd = 100.0 * (lastPx / mtdBase - 1.0);
        return new PeriodReturns(mtd, ytd);
    }

    private static double[] extractAdjCloseSeries(JsonNode resultNode) {
        JsonNode priceSeries = null;
        JsonNode adj = resultNode.path("indicators").path("adjclose");
        if (adj.isArray() && !adj.isEmpty() && adj.get(0) != null) {
            priceSeries = adj.get(0).get("adjclose");
        }
        if (priceSeries == null || !priceSeries.isArray()) {
            JsonNode quote = resultNode.path("indicators").path("quote");
            if (quote.isArray() && !quote.isEmpty() && quote.get(0) != null) {
                priceSeries = quote.get(0).get("close");
            }
        }
        return forwardFillPositivePrice(priceSeries);
    }

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

    private static String encSymbol(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String text(JsonNode n) {
        if (n == null || n.isNull() || !n.isTextual()) {
            return "";
        }
        return n.asText("").trim();
    }

    private static Double dbl(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isNumber()) {
            return n.asDouble();
        }
        if (n.isTextual()) {
            try {
                return Double.parseDouble(n.asText("").trim().replace(",", ""));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String yahooQuoteUrl(String symbol) {
        return "https://finance.yahoo.com/quote/" + encSymbol(symbol);
    }

    private record SectionDef(String title, String subtitle, List<RowDef> rows) {}

    private record RowDef(String symbol, String label) {}

    private record QuoteRow(String symbol, String shortName, String longName, Double price, Double changePctDay) {}

    private record PeriodReturns(Double mtdPct, Double ytdPct) {}
}
