package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.finance.dto.CompanyResearchUpsertRequestDto;
import com.svp.tracker.finance.dto.aitoken.AiTokenFactoryCompanyDto;
import com.svp.tracker.finance.dto.aitoken.AiTokenFactoryDashboardDto;
import com.svp.tracker.finance.dto.aitoken.AiTokenFactoryLayerDto;
import com.svp.tracker.finance.dto.aitoken.AiTokenFactoryWatchRequestDto;
import com.svp.tracker.finance.dto.aitoken.AiTokenFactoryWatchResultDto;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Markets → AI Token Factory: curated AI infrastructure map (hyperscalers → power plant) with Yahoo chart
 * pulse metrics. Heuristic scoring only — not investment advice.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiTokenFactoryService {

    private static final String YAHOO_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String CHART_2Y =
            "https://query1.finance.yahoo.com/v8/finance/chart/%s"
                    + "?range=2y&interval=1d&includeAdjustedClose=true";
    private static final ZoneId NY = ZoneId.of("America/New_York");

    /** Spring Boot 4 does not expose an ObjectMapper bean; local mapper for Yahoo chart JSON. */
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CompanyResearchService companyResearchService;

    public AiTokenFactoryDashboardDto dashboard() {
        List<LayerDef> defs = universe();
        Set<String> symbols = new LinkedHashSet<>();
        for (LayerDef layer : defs) {
            for (CompanyDef c : layer.companies()) {
                if (c.symbol() != null && !c.symbol().isBlank()) {
                    symbols.add(c.symbol().trim().toUpperCase(Locale.ROOT));
                }
            }
        }

        Map<String, PulseQuote> quotes = fetchQuotes(symbols);
        List<String> warnings = new ArrayList<>();
        if (quotes.isEmpty() && !symbols.isEmpty()) {
            warnings.add("No Yahoo chart quotes returned — check network / rate limits.");
        }

        List<AiTokenFactoryLayerDto> layers = new ArrayList<>();
        List<Double> allDay = new ArrayList<>();
        List<Double> allYtd = new ArrayList<>();
        int publicCount = 0;
        int privateCount = 0;

        for (LayerDef layer : defs) {
            List<AiTokenFactoryCompanyDto> rows = new ArrayList<>();
            List<Double> layerDay = new ArrayList<>();
            List<Double> layerYtd = new ArrayList<>();
            for (CompanyDef c : layer.companies()) {
                if (c.symbol() == null || c.symbol().isBlank()) {
                    privateCount++;
                    rows.add(privateCompany(c));
                    continue;
                }
                publicCount++;
                String sym = c.symbol().trim().toUpperCase(Locale.ROOT);
                PulseQuote q = quotes.get(sym);
                AiTokenFactoryCompanyDto row = publicCompany(c, q);
                rows.add(row);
                if (row.changePercentDay() != null) {
                    layerDay.add(row.changePercentDay());
                    allDay.add(row.changePercentDay());
                }
                if (row.changePercentYearToDate() != null) {
                    layerYtd.add(row.changePercentYearToDate());
                    allYtd.add(row.changePercentYearToDate());
                }
            }
            layers.add(new AiTokenFactoryLayerDto(
                    layer.id(),
                    layer.title(),
                    layer.subtitle(),
                    layer.economicsTag(),
                    avg(layerDay),
                    avg(layerYtd),
                    rows));
        }

        Double avgDay = avg(allDay);
        Double avgYtd = avg(allYtd);
        String narrative = buildNarrative(avgDay, avgYtd, publicCount, privateCount);

        return new AiTokenFactoryDashboardDto(
                "AI Token Factory",
                Instant.now().toString(),
                "Curated from the AI infrastructure supply chain map (hyperscalers → power). Yahoo Finance v8/chart "
                        + "pulse (day / MTD / YTD / 52w range). Pulse scores are heuristics for monitoring — "
                        + "not investment advice. Private names have no public quote.",
                narrative,
                avgDay,
                avgYtd,
                publicCount,
                privateCount,
                warnings,
                layers);
    }

    @Transactional
    public AiTokenFactoryWatchResultDto addToWatch(AiTokenFactoryWatchRequestDto req) {
        if (req == null || req.symbols() == null || req.symbols().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbols are required");
        }
        String tag =
                req.thesisTag() == null || req.thesisTag().isBlank() ? "ai-token-factory" : req.thesisTag().trim();
        List<String> done = new ArrayList<>();
        for (String raw : req.symbols()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String symbol = raw.trim().toUpperCase(Locale.ROOT);
            companyResearchService.upsert(
                    new CompanyResearchUpsertRequestDto(
                            symbol, null, "WATCHING", List.of(tag), "Added from AI Token Factory"));
            done.add(symbol);
        }
        return new AiTokenFactoryWatchResultDto(done.size(), done);
    }

    private Map<String, PulseQuote> fetchQuotes(Set<String> symbols) {
        Map<String, PulseQuote> out = new LinkedHashMap<>();
        if (symbols.isEmpty()) {
            return out;
        }
        int timeout = 12_000;
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(12, symbols.size()));
        try {
            Map<String, CompletableFuture<PulseQuote>> futures = new LinkedHashMap<>();
            for (String sym : symbols) {
                futures.put(
                        sym,
                        CompletableFuture.supplyAsync(
                                () -> {
                                    try {
                                        JsonNode root = fetchChart(sym, timeout);
                                        return parsePulse(root, sym);
                                    } catch (Exception e) {
                                        log.debug("AI Token Factory chart failed for {}: {}", sym, e.toString());
                                        return null;
                                    }
                                },
                                pool));
            }
            for (Map.Entry<String, CompletableFuture<PulseQuote>> e : futures.entrySet()) {
                try {
                    PulseQuote q = e.getValue().join();
                    if (q != null) {
                        out.put(e.getKey(), q);
                    }
                } catch (Exception ex) {
                    log.debug("AI Token Factory join failed for {}: {}", e.getKey(), ex.toString());
                }
            }
        } finally {
            pool.shutdownNow();
        }
        return out;
    }

    private JsonNode fetchChart(String symbol, int timeoutMs) throws Exception {
        String enc = URLEncoder.encode(symbol, StandardCharsets.UTF_8).replace("+", "%20");
        String url = String.format(Locale.ROOT, CHART_2Y, enc);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Accept", "application/json")
                .header("User-Agent", YAHOO_UA)
                .build();
        HttpResponse<String> resp =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + resp.statusCode());
        }
        return objectMapper.readTree(resp.body());
    }

    private static PulseQuote parsePulse(JsonNode root, String requested) {
        JsonNode result = root.path("chart").path("result");
        if (!result.isArray() || result.isEmpty()) {
            return null;
        }
        JsonNode r0 = result.get(0);
        JsonNode meta = r0.path("meta");
        Double price = dbl(meta.get("regularMarketPrice"));
        double[] closes = extractAdjClose(r0);
        if (price == null || price <= 0) {
            price = lastClose(closes);
        }
        Double day = dayPct(closes);
        if (day == null) {
            day = dbl(meta.get("regularMarketChangePercent"));
        }
        PeriodReturns pr = mtdYtd(r0);
        Double pct52 = pctOf52w(closes, price);
        return new PulseQuote(
                price,
                day,
                pr == null ? null : pr.mtd(),
                pr == null ? null : pr.ytd(),
                pct52,
                text(meta.get("shortName")));
    }

    private static AiTokenFactoryCompanyDto privateCompany(CompanyDef c) {
        return new AiTokenFactoryCompanyDto(
                c.name(),
                null,
                false,
                c.covered(),
                c.role(),
                c.economicsNote(),
                null,
                null,
                null,
                null,
                null,
                null,
                "Private / no ticker",
                null,
                null,
                List.of("private", c.economicsNote()));
    }

    private static AiTokenFactoryCompanyDto publicCompany(CompanyDef c, PulseQuote q) {
        String sym = c.symbol().trim().toUpperCase(Locale.ROOT);
        List<String> flags = new ArrayList<>();
        flags.add(c.economicsNote());
        if (c.covered()) {
            flags.add("image-watch");
        }
        Integer score = null;
        String label = "No quote";
        if (q != null) {
            score = pulseScore(q);
            label = pulseLabel(score);
            if (q.day() != null && q.day() >= 2.0) {
                flags.add("strong-day");
            }
            if (q.day() != null && q.day() <= -2.0) {
                flags.add("weak-day");
            }
            if (q.pct52() != null && q.pct52() >= 85) {
                flags.add("near-52w-high");
            }
            if (q.pct52() != null && q.pct52() <= 25) {
                flags.add("near-52w-low");
            }
        } else {
            flags.add("quote-missing");
        }
        String yahoo = "https://finance.yahoo.com/quote/" + URLEncoder.encode(sym, StandardCharsets.UTF_8);
        return new AiTokenFactoryCompanyDto(
                c.name(),
                sym,
                true,
                c.covered(),
                c.role(),
                c.economicsNote(),
                q == null ? null : q.price(),
                q == null ? null : q.day(),
                q == null ? null : q.mtd(),
                q == null ? null : q.ytd(),
                q == null ? null : q.pct52(),
                score,
                label,
                yahoo,
                "/markets/research?tab=watch&symbol=" + sym,
                flags);
    }

    /** Heuristic 0–100: blend day / MTD / YTD momentum with 52w location. */
    private static Integer pulseScore(PulseQuote q) {
        double score = 50.0;
        if (q.day() != null) {
            score += clamp(q.day(), -5, 5) * 2.5;
        }
        if (q.mtd() != null) {
            score += clamp(q.mtd(), -15, 15) * 0.8;
        }
        if (q.ytd() != null) {
            score += clamp(q.ytd(), -40, 40) * 0.35;
        }
        if (q.pct52() != null) {
            score += (q.pct52() - 50.0) * 0.25;
        }
        return (int) Math.round(clamp(score, 0, 100));
    }

    private static String pulseLabel(Integer score) {
        if (score == null) {
            return "—";
        }
        if (score >= 72) {
            return "Strong pulse";
        }
        if (score >= 55) {
            return "Constructive";
        }
        if (score >= 40) {
            return "Neutral";
        }
        if (score >= 25) {
            return "Soft";
        }
        return "Weak pulse";
    }

    private static String buildNarrative(Double avgDay, Double avgYtd, int pub, int priv) {
        StringBuilder sb = new StringBuilder();
        sb.append(pub)
                .append(" public tickers and ")
                .append(priv)
                .append(" private names across the AI Token Factory map. ");
        if (avgDay != null) {
            sb.append(String.format(Locale.US, "Average session move %+,.2f%%. ", avgDay));
        }
        if (avgYtd != null) {
            sb.append(String.format(Locale.US, "Average YTD %+,.1f%%. ", avgYtd));
        }
        sb.append("Use layer economics (profit pools vs commoditized) and pulse flags as a monitoring frame, "
                + "then dig into filings and Your Watch notes before any decision.");
        return sb.toString();
    }

    private static Double avg(List<Double> xs) {
        if (xs == null || xs.isEmpty()) {
            return null;
        }
        double s = 0;
        int n = 0;
        for (Double x : xs) {
            if (x != null && !Double.isNaN(x)) {
                s += x;
                n++;
            }
        }
        return n == 0 ? null : s / n;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static Double dbl(JsonNode n) {
        if (n == null || n.isNull() || !n.isNumber()) {
            return null;
        }
        double v = n.asDouble();
        return Double.isNaN(v) ? null : v;
    }

    private static String text(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        String t = n.asText("");
        return t.isBlank() ? null : t.trim();
    }

    private static double[] extractAdjClose(JsonNode resultNode) {
        JsonNode adj = resultNode.path("indicators").path("adjclose");
        if (adj.isArray() && !adj.isEmpty()) {
            JsonNode arr = adj.get(0).get("adjclose");
            return forwardFill(arr);
        }
        JsonNode quote = resultNode.path("indicators").path("quote");
        if (quote.isArray() && !quote.isEmpty()) {
            return forwardFill(quote.get(0).get("close"));
        }
        return new double[0];
    }

    private static double[] forwardFill(JsonNode arr) {
        if (arr == null || !arr.isArray() || arr.isEmpty()) {
            return new double[0];
        }
        int n = arr.size();
        double[] out = new double[n];
        double last = Double.NaN;
        for (int i = 0; i < n; i++) {
            JsonNode v = arr.get(i);
            if (v != null && v.isNumber() && v.asDouble() > 0) {
                last = v.asDouble();
            }
            out[i] = last;
        }
        return out;
    }

    private static Double lastClose(double[] closes) {
        for (int i = closes.length - 1; i >= 0; i--) {
            if (closes[i] > 0 && !Double.isNaN(closes[i])) {
                return closes[i];
            }
        }
        return null;
    }

    private static Double dayPct(double[] closes) {
        if (closes.length < 2) {
            return null;
        }
        int i = closes.length - 1;
        while (i >= 0 && (closes[i] <= 0 || Double.isNaN(closes[i]))) {
            i--;
        }
        int j = i - 1;
        while (j >= 0 && (closes[j] <= 0 || Double.isNaN(closes[j]))) {
            j--;
        }
        if (i < 0 || j < 0 || closes[j] <= 0) {
            return null;
        }
        return 100.0 * (closes[i] / closes[j] - 1.0);
    }

    private static PeriodReturns mtdYtd(JsonNode resultNode) {
        JsonNode ts = resultNode.get("timestamp");
        if (ts == null || !ts.isArray() || ts.isEmpty()) {
            return null;
        }
        int n = ts.size();
        long[] times = new long[n];
        for (int i = 0; i < n; i++) {
            JsonNode t = ts.get(i);
            times[i] = t != null && t.isNumber() ? t.asLong() : 0L;
        }
        double[] closes = extractAdjClose(resultNode);
        if (closes.length != n || n < 2) {
            return null;
        }
        ZonedDateTime nowNy = ZonedDateTime.now(NY);
        int y = nowNy.getYear();
        int m = nowNy.getMonthValue();
        Integer firstYtd = null;
        Integer firstMtd = null;
        for (int i = 0; i < n; i++) {
            if (times[i] <= 0 || closes[i] <= 0 || Double.isNaN(closes[i])) {
                continue;
            }
            LocalDate d = Instant.ofEpochSecond(times[i]).atZone(NY).toLocalDate();
            if (d.getYear() == y && firstYtd == null) {
                firstYtd = i;
            }
            if (d.getYear() == y && d.getMonthValue() == m && firstMtd == null) {
                firstMtd = i;
            }
        }
        Double last = lastClose(closes);
        if (last == null) {
            return null;
        }
        Double ytd = null;
        Double mtd = null;
        if (firstYtd != null && closes[firstYtd] > 0) {
            ytd = 100.0 * (last / closes[firstYtd] - 1.0);
        }
        if (firstMtd != null && closes[firstMtd] > 0) {
            mtd = 100.0 * (last / closes[firstMtd] - 1.0);
        }
        return new PeriodReturns(mtd, ytd);
    }

    private static Double pctOf52w(double[] closes, Double price) {
        if (closes.length < 20 || price == null || price <= 0) {
            return null;
        }
        int from = Math.max(0, closes.length - 252);
        double hi = Double.NEGATIVE_INFINITY;
        double lo = Double.POSITIVE_INFINITY;
        for (int i = from; i < closes.length; i++) {
            double c = closes[i];
            if (c > 0 && !Double.isNaN(c)) {
                hi = Math.max(hi, c);
                lo = Math.min(lo, c);
            }
        }
        if (!(hi > lo) || Double.isInfinite(hi) || Double.isInfinite(lo)) {
            return null;
        }
        return 100.0 * (price - lo) / (hi - lo);
    }

    private static List<LayerDef> universe() {
        return List.of(
                new LayerDef(
                        "demand-hyperscalers",
                        "Demand · Hyperscalers",
                        "Cloud giants funding AI training & inference — CapEx sets factory demand.",
                        "demand",
                        List.of(
                                c("Microsoft", "MSFT", true, "Hyperscaler", "demand"),
                                c("Amazon", "AMZN", true, "Hyperscaler", "demand"),
                                c("Google (Alphabet)", "GOOGL", true, "Hyperscaler", "demand"),
                                c("Meta", "META", true, "Hyperscaler", "demand"),
                                c("Oracle", "ORCL", true, "Hyperscaler", "demand"))),
                new LayerDef(
                        "demand-neoclouds",
                        "Demand · Neoclouds",
                        "GPU-native clouds selling capacity to labs & hyperscalers.",
                        "demand",
                        List.of(
                                c("CoreWeave", "CRWV", true, "Neocloud", "demand"),
                                c("Nebius", "NBIS", true, "Neocloud", "demand"),
                                c("Lambda", null, true, "Neocloud", "private"),
                                c("Crusoe", null, true, "Neocloud", "private"))),
                new LayerDef(
                        "demand-ai-labs",
                        "Demand · AI Labs",
                        "Frontier model builders — mostly private; monitor via partners & spend.",
                        "demand",
                        List.of(
                                c("OpenAI", null, true, "AI Lab", "private"),
                                c("Anthropic", null, true, "AI Lab", "private"),
                                c("xAI", null, true, "AI Lab", "private"))),
                new LayerDef(
                        "layer-6-software",
                        "Layer 6 · Software + Models",
                        "CUDA / stack / models — high-margin software & platform leverage.",
                        "profit_pool",
                        List.of(
                                c("NVIDIA (CUDA ecosystem)", "NVDA", false, "Software + GPUs", "profit_pool"),
                                c("OpenAI", null, true, "Models", "private"),
                                c("Anthropic", null, true, "Models", "private"),
                                c("xAI", null, true, "Models", "private"))),
                new LayerDef(
                        "layer-5-networking",
                        "Layer 5 · Networking + Optics",
                        "Switches, silicon photonics, fiber — scarce interconnects.",
                        "scarce",
                        List.of(
                                c("Arista", "ANET", false, "Networking", "scarce"),
                                c("Broadcom", "AVGO", true, "Networking / silicon", "profit_pool"),
                                c("Astera Labs", "ALAB", true, "Connectivity", "scarce"),
                                c("Coherent", "COHR", true, "Optics", "scarce"),
                                c("Lumentum", "LITE", true, "Optics", "scarce"),
                                c("Fabrinet", "FN", true, "Optics manufacturing", "scarce"),
                                c("Corning", "GLW", false, "Fiber / materials", "scarce"),
                                c("Amphenol", "APH", false, "Interconnect", "scarce"))),
                new LayerDef(
                        "layer-4-memory",
                        "Layer 4 · Memory + Storage",
                        "HBM / DRAM / NAND — scarce AI memory & storage.",
                        "scarce",
                        List.of(
                                c("SK hynix", "000660.KS", true, "HBM / DRAM", "scarce"),
                                c("Samsung Electronics", "005930.KS", false, "Memory / foundry", "scarce"),
                                c("Micron", "MU", true, "HBM / DRAM", "scarce"),
                                c("Seagate", "STX", true, "Storage", "scarce"),
                                c("Western Digital", "WDC", true, "Storage", "scarce"))),
                new LayerDef(
                        "layer-3-servers",
                        "Layer 3 · Servers + Compute",
                        "GPUs, ASICs, servers, packaging — core profit pools.",
                        "profit_pool",
                        List.of(
                                c("NVIDIA", "NVDA", false, "GPUs", "profit_pool"),
                                c("AMD", "AMD", false, "GPUs / CPUs", "profit_pool"),
                                c("Broadcom", "AVGO", true, "ASICs / networking", "profit_pool"),
                                c("Marvell", "MRVL", true, "Custom silicon", "profit_pool"),
                                c("Dell", "DELL", false, "Servers", "commoditized"),
                                c("Super Micro", "SMCI", true, "AI servers", "commoditized"),
                                c("Foxconn", "2317.TW", false, "Assembly", "commoditized"),
                                c("Quanta", "2382.TW", false, "ODM servers", "commoditized"),
                                c("TSMC", "TSM", false, "Foundry / packaging", "scarce"))),
                new LayerDef(
                        "layer-2-cooling",
                        "Layer 2 · Cooling",
                        "Thermal & power quality for dense GPU halls.",
                        "scarce",
                        List.of(
                                c("Vertiv", "VRT", true, "Cooling / power", "scarce"),
                                c("Eaton", "ETN", true, "Power quality", "scarce"),
                                c("Schneider Electric", "SBGSY", true, "Cooling / power", "scarce"),
                                c("nVent", "NVT", false, "Cooling", "scarce"))),
                new LayerDef(
                        "layer-1-power",
                        "Layer 1 · Power + Physical Plant",
                        "Generation, grid, colocation — scarce watts & buildings (~20% of GW cost).",
                        "scarce",
                        List.of(
                                c("GE Vernova", "GEV", true, "Power equipment", "scarce"),
                                c("Constellation", "CEG", true, "Nuclear / power", "scarce"),
                                c("Vistra", "VST", true, "Power generation", "scarce"),
                                c("Bloom Energy", "BE", true, "Fuel cells", "scarce"),
                                c("Vertiv", "VRT", true, "Data center infra", "scarce"),
                                c("Eaton", "ETN", true, "Electrical", "scarce"),
                                c("Schneider Electric", "SBGSY", true, "Electrical", "scarce"),
                                c("Caterpillar", "CAT", true, "Power / gensets", "scarce"),
                                c("Equinix", "EQIX", false, "Colocation", "scarce"),
                                c("Digital Realty", "DLR", false, "Colocation", "scarce"))));
    }

    private static CompanyDef c(
            String name, String symbol, boolean covered, String role, String economicsNote) {
        return new CompanyDef(name, symbol, covered, role, economicsNote);
    }

    private record LayerDef(
            String id, String title, String subtitle, String economicsTag, List<CompanyDef> companies) {}

    private record CompanyDef(
            String name, String symbol, boolean covered, String role, String economicsNote) {}

    private record PulseQuote(
            Double price, Double day, Double mtd, Double ytd, Double pct52, String shortName) {}

    private record PeriodReturns(Double mtd, Double ytd) {}
}
