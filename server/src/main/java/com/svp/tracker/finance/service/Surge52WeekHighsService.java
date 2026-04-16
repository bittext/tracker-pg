package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.finance.dto.Surge52WeekHighsDto;
import com.svp.tracker.finance.dto.Surge52WeekRowDto;
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
 * Scans for recent 52-week price gainers (from daily adjusted closes) among Yahoo screener names, then ranks by
 * trailing-year total return and proximity to rolling 52-week highs. Not investment advice.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Surge52WeekHighsService {

    private static final String SOURCE =
            "Yahoo screeners + 52w total return + rolling 52w proximity + quote metrics + heuristic growth copy";
    private static final String SCREENER_BASE =
            "https://query1.finance.yahoo.com/v1/finance/screener/predefined/saved"
                    + "?formatted=true&lang=en-US&region=US&count=%d&scrIds=%s";
    private static final String CHART_DAILY =
            "https://query1.finance.yahoo.com/v8/finance/chart/%s"
                    + "?range=3y&interval=1d&includeAdjustedClose=true";

    private static final int LOOKBACK_TRADING_DAYS = 252;
    private static final int SIX_MONTH_TRADING_DAYS = 126;
    private static final int MIN_UI_ROWS = 10;
    /** Close within this fraction of the rolling 252-day high counts as “near the top”. */
    private static final double NEAR_52W_FRACTION = 0.99;
    /**
     * Regular price within this percent of the quote 52-week high counts as “at” the 52w high (rounding / session
     * noise).
     */
    private static final double AT_52W_PRICE_MIN_PCT = 99.5;

    private final FinanceProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Surge52WeekHighsDto fetchSurgeNear52WeekHighs(Integer limitRaw) {
        int limit = sanitizeLimit(limitRaw);
        int fetchCount = Math.min(120, Math.max(60, limit * 4));
        int poolLimit = Math.min(120, Math.max(60, limit * 5));

        JsonNode gainers = fetchScreenerJson("day_gainers", fetchCount);
        JsonNode aggressive = fetchScreenerJsonOptional("aggressive_small_gainers", Math.min(80, fetchCount));

        Map<String, JsonNode> quoteBySymbol = mergeQuotes(gainers, aggressive);
        List<String> symbols = orderedUnionSymbolsTwo(gainers, aggressive, poolLimit);

        List<Surge52WeekRowDto> enriched = enrichWithPersistence(symbols, quoteBySymbol);

        enriched.sort(
                Comparator.comparing(Surge52WeekRowDto::fiftyTwoWeekGainPercent, Comparator.nullsLast(Double::compareTo))
                        .reversed()
                        .thenComparingDouble(Surge52WeekRowDto::pctPastYearNearRolling52WeekHigh)
                        .reversed()
                        .thenComparingDouble(Surge52WeekRowDto::pctSixMonthsNearRolling52WeekHigh)
                        .reversed()
                        .thenComparingInt(Surge52WeekRowDto::daysNearRolling52WeekHigh)
                        .reversed()
                        .thenComparing(Surge52WeekRowDto::repeatedStayAtTop, Comparator.reverseOrder())
                        .thenComparingDouble(Surge52WeekRowDto::momentumScore)
                        .reversed());

        List<Surge52WeekRowDto> filtered = selectTopRowsWithFallback(enriched, limit);

        String note =
                "Universe: day gainers and aggressive small gainers (deduped). Ranking is by trailing ~252-session "
                        + "adjusted-close return, then rolling-52w persistence. Conditions used: (1) strict: positive "
                        + "52w return + full-year persistence window; (2) relaxed: positive 52w return + either 6-month "
                        + "or 1-year proximity score, or current price at least ~95% of 52w high; (3) fallback fill: "
                        + "top remaining positive-return names by ranking so the UI can list at least 10 when available.";

        return new Surge52WeekHighsDto(
                SOURCE, Instant.now().toString(), filtered.size(), note, filtered);
    }

    /**
     * Names whose regular price is at the 52-week high from the Yahoo quote (within a small tolerance for rounding).
     * Scans multiple Yahoo predefined screeners (saved lists), merges quotes, then filters.
     */
    public Surge52WeekHighsDto fetchRecent52WeekHighRisers(Integer limitRaw) {
        int limit = sanitizeLimit(limitRaw);
        int perScreener = 120;
        int maxSymbolUnion = Math.min(360, Math.max(200, limit * 40));
        int maxIterationsPerScreener = 90;

        JsonNode dayGainers = fetchScreenerJson("day_gainers", perScreener);
        JsonNode mostActives = fetchScreenerJsonOptional("most_actives", perScreener);
        JsonNode aggressive = fetchScreenerJsonOptional("aggressive_small_gainers", perScreener);
        JsonNode undervaluedGrowth = fetchScreenerJsonOptional("undervalued_growth_stocks", 80);
        JsonNode smallCapGainers = fetchScreenerJsonOptional("small_cap_gainers", 80);
        JsonNode allTimeHigh = fetchScreenerJsonOptional("all_time_high", 80);

        JsonNode[] roots = {
            dayGainers, mostActives, aggressive, undervaluedGrowth, smallCapGainers, allTimeHigh
        };

        Map<String, JsonNode> quoteBySymbol = mergeQuotesOrdered(roots);
        List<String> symbols = unionSymbolsFair(maxIterationsPerScreener, maxSymbolUnion, roots);

        List<Surge52WeekRowDto> enriched = enrichWithPersistence(symbols, quoteBySymbol);

        List<Surge52WeekRowDto> atHigh = new ArrayList<>();
        for (Surge52WeekRowDto r : enriched) {
            if (r.percentOf52WeekHigh() >= AT_52W_PRICE_MIN_PCT) {
                atHigh.add(r);
            }
        }
        atHigh.sort(
                Comparator.comparingDouble(Surge52WeekRowDto::percentOf52WeekHigh)
                        .reversed()
                        .thenComparing(
                                Surge52WeekRowDto::fiftyTwoWeekGainPercent,
                                Comparator.nullsLast(Double::compareTo))
                        .reversed());

        if (atHigh.size() > limit) {
            atHigh = new ArrayList<>(atHigh.subList(0, limit));
        }

        String note =
                "Scanned Yahoo predefined screeners (day_gainers, most_actives, aggressive_small_gainers, "
                        + "undervalued_growth_stocks, small_cap_gainers, all_time_high — optional lists skipped if "
                        + "unavailable). Up to "
                        + maxIterationsPerScreener
                        + " symbols taken per list, "
                        + maxSymbolUnion
                        + " max after dedupe. Shown rows: price ≥ "
                        + String.format(Locale.US, "%.1f", AT_52W_PRICE_MIN_PCT)
                        + "% of 52-week high (Yahoo quote). Heuristic, not investment advice.";

        return new Surge52WeekHighsDto(
                "At 52-week high (Yahoo quote)", Instant.now().toString(), atHigh.size(), note, atHigh);
    }

    private int sanitizeLimit(Integer raw) {
        int cap = Math.min(50, props.newsMaxItems() > 0 ? props.newsMaxItems() : 10);
        if (raw == null) {
            return cap;
        }
        if (raw < 1) {
            return 1;
        }
        return Math.min(raw, cap);
    }

    private static void appendIfMissing(List<Surge52WeekRowDto> out, Surge52WeekRowDto row) {
        for (Surge52WeekRowDto existing : out) {
            if (existing.symbol().equalsIgnoreCase(row.symbol())) {
                return;
            }
        }
        out.add(row);
    }

    private static boolean containsSymbol(List<Surge52WeekRowDto> rows, String symbol) {
        for (Surge52WeekRowDto r : rows) {
            if (r.symbol().equalsIgnoreCase(symbol)) {
                return true;
            }
        }
        return false;
    }

    private List<Surge52WeekRowDto> selectTopRowsWithFallback(List<Surge52WeekRowDto> ranked, int limit) {
        int target = Math.max(Math.min(limit, ranked.size()), Math.min(MIN_UI_ROWS, ranked.size()));
        List<Surge52WeekRowDto> out = new ArrayList<>(target);

        // Tier 1 (strict): positive 52w gain with full rolling-year window.
        for (Surge52WeekRowDto r : ranked) {
            Double gain = r.fiftyTwoWeekGainPercent();
            if (gain != null && gain > 0 && r.pastYearTradingDays() >= LOOKBACK_TRADING_DAYS) {
                appendIfMissing(out, r);
                if (out.size() >= target) {
                    return out;
                }
            }
        }

        // Tier 2 (relaxed): still positive 52w gain, but allow partial persistence signals.
        for (Surge52WeekRowDto r : ranked) {
            Double gain = r.fiftyTwoWeekGainPercent();
            if (gain != null
                    && gain > 0
                    && (r.pctPastYearNearRolling52WeekHigh() >= 5.0
                            || r.pctSixMonthsNearRolling52WeekHigh() >= 6.0
                            || r.percentOf52WeekHigh() >= 95.0)) {
                appendIfMissing(out, r);
                if (out.size() >= target) {
                    return out;
                }
            }
        }

        // Tier 3 (fallback): fill with remaining positive 52w gainers in ranked order.
        for (Surge52WeekRowDto r : ranked) {
            Double gain = r.fiftyTwoWeekGainPercent();
            if (gain != null && gain > 0) {
                appendIfMissing(out, r);
                if (out.size() >= target) {
                    return out;
                }
            }
        }

        // Tier 4 (hard fallback): chart return missing, but quote + persistence still indicate near-high risers.
        for (Surge52WeekRowDto r : ranked) {
            if (r.percentOf52WeekHigh() >= 95.0
                    && (r.regularMarketChangePercent() == null || r.regularMarketChangePercent() >= -1.5)) {
                appendIfMissing(out, r);
                if (out.size() >= target) {
                    return out;
                }
            }
        }

        if (out.size() > limit) {
            return new ArrayList<>(out.subList(0, limit));
        }
        return out;
    }

    private List<Surge52WeekRowDto> enrichWithPersistence(List<String> symbols, Map<String, JsonNode> quoteBySymbol) {
        if (symbols.isEmpty()) {
            return List.of();
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Surge52WeekRowDto>> futures = new ArrayList<>(symbols.size());
            for (String sym : symbols) {
                futures.add(
                        pool.submit(
                                () ->
                                        buildOneRow(sym, quoteBySymbol.get(sym.toUpperCase(Locale.ROOT)))));
            }
            List<Surge52WeekRowDto> out = new ArrayList<>(symbols.size());
            for (int i = 0; i < futures.size(); i++) {
                try {
                    Surge52WeekRowDto row = futures.get(i).get();
                    if (row != null) {
                        out.add(row);
                    }
                } catch (ExecutionException e) {
                    log.debug("persistence task failed for {}", symbols.get(i), e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while loading chart data", e);
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

    private Surge52WeekRowDto buildOneRow(String symbol, JsonNode quote) {
        JsonNode chart;
        try {
            chart = fetchChartDaily(symbol);
        } catch (Exception e) {
            log.debug("chart fetch failed for {}", symbol, e);
            if (quote != null && !quote.isNull()) {
                return rowFromScreenerQuote(quote, Persistence.empty(), false);
            }
            return null;
        }
        Persistence p = computeRolling52WeekPersistence(chart);
        boolean repeatedStay =
                p.evalDays > 0
                        && (p.pct >= 18.0
                                || (p.pct >= 15.0 && p.nearDays >= 45)
                                || (p.sixPct >= 22.0 && p.sixEvalDays >= 60)
                                || (p.fiftyTwoWeekGainPercent() != null && p.fiftyTwoWeekGainPercent() >= 45.0));

        if (quote != null && !quote.isNull()) {
            return rowFromScreenerQuote(quote, p, repeatedStay);
        }
        return rowFromChartMeta(chart, symbol, p, repeatedStay, null);
    }

    private Surge52WeekRowDto rowFromScreenerQuote(JsonNode q, Persistence p, boolean repeatedStay) {
        String symbol = text(q, "symbol");
        if (symbol == null || symbol.isBlank()) {
            symbol = "?";
        }
        Double price = dbl(q, "regularMarketPrice");
        Double high52 = dbl(q, "fiftyTwoWeekHigh");
        Double chgPct = dbl(q, "regularMarketChangePercent");
        Double highChgPct = dbl(q, "fiftyTwoWeekHighChangePercent");
        double pctOfHigh = 0.0;
        if (price != null && high52 != null && high52 > 0) {
            pctOfHigh = round2(100.0 * price / high52);
        }
        double momentum = momentumFromQuote(chgPct, highChgPct, pctOfHigh);

        String name = text(q, "shortName");
        if (name == null || name.isBlank()) {
            name = text(q, "longName");
        }
        if (name == null) {
            name = symbol;
        }

        return completeSurgeRow(
                symbol.trim(),
                name,
                price,
                chgPct,
                high52,
                highChgPct,
                pctOfHigh,
                round2(momentum),
                p,
                repeatedStay,
                q);
    }

    private Surge52WeekRowDto rowFromChartMeta(
            JsonNode chartRoot, String symbol, Persistence p, boolean repeatedStay, JsonNode quote) {
        JsonNode meta = chartRoot.path("chart").path("result").path(0).path("meta");
        JsonNode fundamentals = quote != null && !quote.isNull() ? quote : meta;
        String name = text(meta, "shortName");
        if (name == null || name.isBlank()) {
            name = text(meta, "longName");
        }
        if (name == null) {
            name = symbol;
        }
        Double price = dbl(meta, "regularMarketPrice");
        Double high52 = dbl(meta, "fiftyTwoWeekHigh");
        Double prev = dbl(meta, "previousClose");
        Double chgPct = null;
        if (price != null && prev != null && prev > 0) {
            chgPct = round2(100.0 * (price - prev) / prev);
        }
        double pctOfHigh = 0.0;
        if (price != null && high52 != null && high52 > 0) {
            pctOfHigh = round2(100.0 * price / high52);
        }
        double momentum = momentumFromQuote(chgPct, null, pctOfHigh);

        return completeSurgeRow(
                symbol.trim(),
                name,
                price,
                chgPct,
                high52,
                null,
                pctOfHigh,
                round2(momentum),
                p,
                repeatedStay,
                fundamentals);
    }

    private Surge52WeekRowDto completeSurgeRow(
            String symbol,
            String name,
            Double price,
            Double chgPct,
            Double high52,
            Double highChgPct,
            double pctOfHigh,
            double momentum,
            Persistence p,
            boolean repeatedStay,
            JsonNode fundamentals) {
        Double marketCap = dbl(fundamentals, "marketCap");
        Double low52 = dbl(fundamentals, "fiftyTwoWeekLow");
        Long vol3m = longNode(fundamentals, "averageDailyVolume3Month");
        if (vol3m == null) {
            vol3m = longNode(fundamentals, "averageDailyVolume10Day");
        }
        Double trailPe = dbl(fundamentals, "trailingPE");
        if (trailPe == null) {
            trailPe = dbl(fundamentals, "trailingPe");
        }
        Double fwdPe = dbl(fundamentals, "forwardPE");
        if (fwdPe == null) {
            fwdPe = dbl(fundamentals, "forwardPe");
        }
        String url = externalQuoteUrl(symbol);
        Double gain = p.fiftyTwoWeekGainPercent();
        String outlook =
                growthOutlookLabel(p, pctOfHigh, chgPct, trailPe, fwdPe, repeatedStay, gain);
        String summary =
                growthProspectsSummary(
                        name,
                        symbol,
                        p,
                        pctOfHigh,
                        chgPct,
                        trailPe,
                        fwdPe,
                        low52,
                        high52,
                        price,
                        repeatedStay,
                        gain);
        return new Surge52WeekRowDto(
                symbol.trim(),
                name,
                price,
                chgPct,
                high52,
                highChgPct,
                pctOfHigh,
                round2(momentum),
                p.evalDays,
                p.nearDays,
                round2(p.pct),
                repeatedStay,
                gain,
                p.sixEvalDays,
                p.sixNearDays,
                round2(p.sixPct),
                marketCap,
                low52,
                vol3m,
                trailPe,
                fwdPe,
                outlook,
                summary,
                url);
    }

    private static String externalQuoteUrl(String symbol) {
        String s = symbol.trim().replace("^", "%5E");
        return "https://finance.yahoo.com/quote/" + s;
    }

    private static String growthOutlookLabel(
            Persistence p,
            double pctOfHigh,
            Double chgPct,
            Double trailPe,
            Double fwdPe,
            boolean repeatedStay,
            Double fiftyTwoWeekGainPercent) {
        double chg = chgPct != null ? chgPct : 0.0;
        if (fiftyTwoWeekGainPercent != null && fiftyTwoWeekGainPercent >= 120 && pctOfHigh >= 98.5) {
            return "Extended";
        }
        if (fiftyTwoWeekGainPercent != null && fiftyTwoWeekGainPercent >= 40 && p.pct >= 15.0) {
            return "Constructive";
        }
        if (pctOfHigh >= 99.7 && chg >= 3.0 && p.pct >= 25.0) {
            return "Extended";
        }
        if (repeatedStay && p.pct >= 20.0 && chg >= 0) {
            return "Constructive";
        }
        if (trailPe != null && trailPe > 45 && pctOfHigh >= 99.0) {
            return "Cautious";
        }
        if (fwdPe != null && trailPe != null && fwdPe < trailPe * 0.85 && p.pct >= 15.0) {
            return "Constructive";
        }
        if (chg < -1.5 && pctOfHigh >= 98.0) {
            return "Mixed";
        }
        if (p.sixPct >= 22.0 && p.pct >= 15.0) {
            return "Constructive";
        }
        return "Neutral";
    }

    private static String growthProspectsSummary(
            String name,
            String symbol,
            Persistence p,
            double pctOfHigh,
            Double chgPct,
            Double trailPe,
            Double fwdPe,
            Double low52,
            Double high52,
            Double price,
            boolean repeatedStay,
            Double fiftyTwoWeekGainPercent) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(" (").append(symbol).append("). ");
        if (fiftyTwoWeekGainPercent != null) {
            sb.append("Adjusted closes are up roughly ")
                    .append(String.format(Locale.US, "%.1f", fiftyTwoWeekGainPercent))
                    .append("% over the last ~252 trading sessions versus a year-ago level — a recent 52-week gain "
                            + "profile. ");
        }
        sb.append("It has spent roughly ")
                .append(String.format(Locale.US, "%.0f", p.pct))
                .append("% of the last ")
                .append(p.evalDays)
                .append(" sessions within about 1% of its rolling 52-week high");
        if (p.sixEvalDays > 0) {
            sb.append(", including ")
                    .append(String.format(Locale.US, "%.0f", p.sixPct))
                    .append("% of the most recent ")
                    .append(p.sixEvalDays)
                    .append(" sessions");
        }
        sb.append(". ");
        if (repeatedStay) {
            sb.append("That persistence, combined with repeated closes near the annual range ceiling, often reflects "
                    + "strong relative strength, though it can also precede consolidation. ");
        } else {
            sb.append("Participation near the top of the range is meaningful but less extreme than the strongest "
                    + "names in this screen. ");
        }
        if (chgPct != null) {
            if (chgPct >= 2.0) {
                sb.append("Today’s session is adding momentum on top of that structure. ");
            } else if (chgPct <= -1.5) {
                sb.append("The stock is pulling back slightly while still near its 52-week high context. ");
            }
        }
        if (trailPe != null) {
            sb.append("Trailing P/E near ").append(String.format(Locale.US, "%.1f", trailPe)).append(" ");
            if (trailPe > 40) {
                sb.append("suggests the market is paying a richer multiple — growth needs to validate the price. ");
            } else if (trailPe < 18) {
                sb.append("reads comparatively moderate versus many growth peers at similar price strength. ");
            } else {
                sb.append("sits in a middle ground versus typical large-cap tech. ");
            }
        } else {
            sb.append("Valuation multiples were not available on this feed. ");
        }
        if (low52 != null && high52 != null && high52 > low52 && price != null) {
            double span = 100.0 * (price - low52) / (high52 - low52);
            sb.append("Price is about ")
                    .append(String.format(Locale.US, "%.0f", span))
                    .append("% of the way from the 52-week low to the 52-week high. ");
        }
        sb.append("This is an automated, non-exhaustive view — verify fundamentals and catalysts independently.");
        return sb.toString();
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
            try {
                return Math.round(Double.parseDouble(v.asText().trim()));
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static double momentumFromQuote(Double chgPct, Double highChgPct, double pctOfHigh) {
        double chg = chgPct != null ? chgPct : 0.0;
        double momentum =
                chg * 2.0
                        + Math.min(15.0, Math.max(0.0, pctOfHigh - 99.0) * 10.0)
                        + (highChgPct != null ? Math.min(10.0, Math.max(0.0, highChgPct)) : 0.0);
        return momentum;
    }

    private record Persistence(
            int evalDays,
            int nearDays,
            double pct,
            int sixEvalDays,
            int sixNearDays,
            double sixPct,
            Double fiftyTwoWeekGainPercent) {
        static Persistence empty() {
            return new Persistence(0, 0, 0.0, 0, 0, 0.0, null);
        }
    }

    /** Total return over the last 252 trading sessions: last close vs close 252 sessions earlier. */
    private static Double compute52WeekGainPercent(double[] closes, int n) {
        if (n < LOOKBACK_TRADING_DAYS + 1) {
            return null;
        }
        int lastIdx = n - 1;
        int baseIdx = lastIdx - LOOKBACK_TRADING_DAYS;
        double base = closes[baseIdx];
        double last = closes[lastIdx];
        if (base <= 0) {
            return null;
        }
        return round2(100.0 * (last / base - 1.0));
    }

    private static Persistence computeRolling52WeekPersistence(JsonNode root) {
        JsonNode result = root.path("chart").path("result");
        if (!result.isArray() || result.isEmpty()) {
            return Persistence.empty();
        }
        JsonNode r = result.get(0);
        double[] closes = extractClosesForwardFilled(r);
        int n = closes.length;
        Double gain = compute52WeekGainPercent(closes, n);
        if (closes.length < LOOKBACK_TRADING_DAYS * 2) {
            return new Persistence(0, 0, 0.0, 0, 0, 0.0, gain);
        }
        int start = n - LOOKBACK_TRADING_DAYS;
        if (start < LOOKBACK_TRADING_DAYS - 1) {
            return new Persistence(0, 0, 0.0, 0, 0, 0.0, gain);
        }
        int near = 0;
        for (int i = start; i < n; i++) {
            double hi = maxInRange(closes, i - LOOKBACK_TRADING_DAYS + 1, i + 1);
            if (hi > 0 && closes[i] >= NEAR_52W_FRACTION * hi) {
                near++;
            }
        }
        double pct = 100.0 * near / LOOKBACK_TRADING_DAYS;

        int firstIdxForRolling = LOOKBACK_TRADING_DAYS - 1;
        int sixStart = Math.max(firstIdxForRolling, n - SIX_MONTH_TRADING_DAYS);
        int sixEvalDays = n - sixStart;
        int sixNear = 0;
        double sixPct = 0.0;
        if (sixEvalDays > 0) {
            for (int i = sixStart; i < n; i++) {
                double hi = maxInRange(closes, i - LOOKBACK_TRADING_DAYS + 1, i + 1);
                if (hi > 0 && closes[i] >= NEAR_52W_FRACTION * hi) {
                    sixNear++;
                }
            }
            sixPct = 100.0 * sixNear / sixEvalDays;
        }

        return new Persistence(LOOKBACK_TRADING_DAYS, near, pct, sixEvalDays, sixNear, sixPct, gain);
    }

    private static double maxInRange(double[] a, int from, int to) {
        double m = Double.NEGATIVE_INFINITY;
        for (int i = from; i < to; i++) {
            if (a[i] > m) {
                m = a[i];
            }
        }
        return m;
    }

    private static double[] extractClosesForwardFilled(JsonNode resultNode) {
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
        if (priceSeries == null || !priceSeries.isArray()) {
            return new double[0];
        }
        int n = priceSeries.size();
        double[] raw = new double[n];
        int valid = 0;
        for (int i = 0; i < n; i++) {
            JsonNode px = priceSeries.get(i);
            if (px != null && !px.isNull() && px.isNumber()) {
                double v = px.asDouble();
                if (v > 0) {
                    raw[i] = v;
                    valid++;
                } else {
                    raw[i] = Double.NaN;
                }
            } else {
                raw[i] = Double.NaN;
            }
        }
        if (valid == 0) {
            return new double[0];
        }
        double last = 0.0;
        for (int i = 0; i < n; i++) {
            if (Double.isNaN(raw[i])) {
                raw[i] = last;
            } else {
                last = raw[i];
            }
        }
        return raw;
    }

    private JsonNode fetchChartDaily(String symbol) throws Exception {
        String enc = URLEncoder.encode(symbol, StandardCharsets.UTF_8).replace("+", "%20");
        String url = String.format(Locale.ROOT, CHART_DAILY, enc);
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
            log.warn("Screener fetch failed: {}", scrId, e);
            throw new IllegalStateException("Could not load screener " + scrId, e);
        }
    }

    private JsonNode fetchScreenerJsonOptional(String scrId, int count) {
        try {
            return fetchScreenerJson(scrId, count);
        } catch (Exception e) {
            log.debug("Optional screener {} skipped: {}", scrId, e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private static Map<String, JsonNode> mergeQuotes(JsonNode gainersRoot, JsonNode secondRoot) {
        Map<String, JsonNode> map = new HashMap<>();
        appendQuotes(gainersRoot, map, false);
        appendQuotes(secondRoot, map, true);
        return map;
    }

    /** Earlier roots win when the same symbol appears in multiple screeners. */
    private static Map<String, JsonNode> mergeQuotesOrdered(JsonNode[] roots) {
        Map<String, JsonNode> map = new HashMap<>();
        for (JsonNode root : roots) {
            if (root != null) {
                appendQuotes(root, map, true);
            }
        }
        return map;
    }

    /**
     * Builds a deduped symbol list: for each screener in order, take up to {@code maxIterationsPerScreener} symbols from
     * that list’s order, stopping at {@code maxTotalSymbols} overall.
     */
    private static List<String> unionSymbolsFair(
            int maxIterationsPerScreener, int maxTotalSymbols, JsonNode[] roots) {
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

    private static List<String> orderedUnionSymbolsTwo(JsonNode firstRoot, JsonNode secondRoot, int maxSymbols) {
        LinkedHashSet<String> order = new LinkedHashSet<>();
        addSymbolsInOrder(firstRoot, order, maxSymbols);
        if (order.size() < maxSymbols) {
            addSymbolsInOrder(secondRoot, order, maxSymbols);
        }
        return new ArrayList<>(order);
    }

    private static void addSymbolsInOrder(JsonNode root, LinkedHashSet<String> order, int maxTotal) {
        JsonNode quotes = firstResultQuotes(root);
        if (quotes == null || !quotes.isArray()) {
            return;
        }
        for (JsonNode q : quotes) {
            if (order.size() >= maxTotal) {
                return;
            }
            String sym = text(q, "symbol");
            if (sym != null && !sym.isBlank()) {
                order.add(sym.trim());
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
