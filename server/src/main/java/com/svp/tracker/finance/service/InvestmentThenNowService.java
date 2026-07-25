package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.finance.domain.FinanceInvestmentThenNow;
import com.svp.tracker.finance.dto.InvestmentThenNowOverlayPointDto;
import com.svp.tracker.finance.dto.InvestmentThenNowOverlayResponseDto;
import com.svp.tracker.finance.dto.InvestmentThenNowOverlaySeriesDto;
import com.svp.tracker.finance.dto.InvestmentThenNowRequestDto;
import com.svp.tracker.finance.dto.InvestmentThenNowResultDto;
import com.svp.tracker.finance.repository.FinanceInvestmentThenNowRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Answers “$X invested on date in SYMBOL — worth now?” using Nasdaq daily closes (Alpha Vantage fallback),
 * and can persist the answer per user for reference.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvestmentThenNowService {

    private static final ZoneId NY = ZoneId.of("America/New_York");
    private static final String BROWSER_UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/122.0.0.0 Safari/537.36";
    private static final String NASDAQ_CHART_URL =
            "https://api.nasdaq.com/api/quote/%s/chart?assetclass=%s&fromdate=%s&todate=%s";
    private static final List<String> NASDAQ_ASSET_CLASSES = List.of("stocks", "etf");
    private static final BigDecimal DEFAULT_INVESTED = new BigDecimal("78198.72");
    private static final LocalDate DEFAULT_AS_OF = LocalDate.of(2026, 6, 28);
    private static final DateTimeFormatter LONG_DATE =
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US);
    private static final DateTimeFormatter NASDAQ_DAY = DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US);
    private static final DateTimeFormatter ISO_DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Duration CHART_CACHE_TTL = Duration.ofMinutes(10);
    private static final List<String> SERIES_COLORS = List.of(
            "#4f46e5", "#0d9488", "#c026d3", "#ea580c", "#2563eb", "#16a34a", "#db2777", "#ca8a04");

    private final FinanceProperties props;
    private final CurrentUserService currentUser;
    private final FinanceInvestmentThenNowRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, CachedChart> chartCache = new ConcurrentHashMap<>();
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    @Transactional(readOnly = true)
    public List<InvestmentThenNowResultDto> listSaved() {
        long owner = currentUser.requireUserId();
        return repository.findByOwnerUserIdOrderByUpdatedAtDesc(owner).stream()
                .map(r -> toDto(r, true))
                .toList();
    }

    @Transactional(readOnly = true)
    public InvestmentThenNowResultDto getSaved(long id) {
        long owner = currentUser.requireUserId();
        FinanceInvestmentThenNow row = repository
                .findByIdAndOwnerUserId(id, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved answer not found"));
        return toDto(row, true);
    }

    @Transactional
    public void deleteSaved(long id) {
        long owner = currentUser.requireUserId();
        FinanceInvestmentThenNow row = repository
                .findByIdAndOwnerUserId(id, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved answer not found"));
        repository.delete(row);
    }

    /**
     * Historical overlay for all saved scenarios: each series has daily % (rebased to 100 at as-of)
     * and $ portfolio value (shares × close).
     */
    @Transactional(readOnly = true)
    public InvestmentThenNowOverlayResponseDto overlaySeries() {
        long owner = currentUser.requireUserId();
        List<FinanceInvestmentThenNow> rows = repository.findByOwnerUserIdOrderByUpdatedAtDesc(owner);
        if (rows.isEmpty()) {
            return new InvestmentThenNowOverlayResponseDto(List.of(), List.of());
        }
        LocalDate today = LocalDate.now(NY);
        List<InvestmentThenNowOverlaySeriesDto> series = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int colorIdx = 0;
        for (FinanceInvestmentThenNow row : rows) {
            String color = SERIES_COLORS.get(colorIdx % SERIES_COLORS.size());
            colorIdx++;
            try {
                ChartSnapshot chart = loadPrices(row.getSymbol(), row.getAsOfDate(), today);
                NavigableMap.Entry<LocalDate, Double> asOfBar =
                        chart.bars().floorEntry(row.getAsOfDate());
                if (asOfBar == null || asOfBar.getValue() <= 0) {
                    warnings.add(row.getSymbol() + ": no close on or before " + row.getAsOfDate());
                    continue;
                }
                double asOfClose = asOfBar.getValue();
                BigDecimal shares = row.getShares();
                List<InvestmentThenNowOverlayPointDto> points = new ArrayList<>();
                for (var e : chart.bars().tailMap(asOfBar.getKey(), true).entrySet()) {
                    double close = e.getValue();
                    if (!Double.isFinite(close) || close <= 0) {
                        continue;
                    }
                    BigDecimal valuePct = BigDecimal.valueOf(close / asOfClose * 100.0)
                            .setScale(4, RoundingMode.HALF_UP);
                    BigDecimal valueUsd = shares
                            .multiply(BigDecimal.valueOf(close))
                            .setScale(2, RoundingMode.HALF_UP);
                    points.add(new InvestmentThenNowOverlayPointDto(
                            e.getKey(), valuePct, valueUsd, money6(close)));
                }
                if (points.isEmpty()) {
                    warnings.add(row.getSymbol() + ": empty series after as-of");
                    continue;
                }
                series.add(new InvestmentThenNowOverlaySeriesDto(
                        row.getId(),
                        row.getSymbol(),
                        row.getCompanyName(),
                        row.getInvestedAmount(),
                        row.getAsOfDate(),
                        row.getPriceAsOfSession(),
                        row.getShares(),
                        color,
                        points));
            } catch (Exception e) {
                warnings.add(row.getSymbol() + ": " + (e.getMessage() == null ? e.toString() : e.getMessage()));
                log.warn("then/now overlay failed for {} id={}", row.getSymbol(), row.getId(), e);
            }
        }
        return new InvestmentThenNowOverlayResponseDto(List.copyOf(series), List.copyOf(warnings));
    }

    @Transactional
    public InvestmentThenNowResultDto compute(InvestmentThenNowRequestDto body) {
        String symbol = normalizeSymbol(body == null ? null : body.symbol());
        if (symbol.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbol is required");
        }
        BigDecimal invested = body != null && body.investedAmount() != null
                ? body.investedAmount()
                : DEFAULT_INVESTED;
        if (invested.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "investedAmount must be positive");
        }
        invested = invested.setScale(2, RoundingMode.HALF_UP);

        LocalDate asOf = body != null && body.asOfDate() != null ? body.asOfDate() : DEFAULT_AS_OF;
        LocalDate today = LocalDate.now(NY);
        if (asOf.isAfter(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "asOfDate cannot be in the future");
        }

        ChartSnapshot chart = loadPrices(symbol, asOf, today);
        if (chart.bars().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No price history returned for " + symbol);
        }

        NavigableMap.Entry<LocalDate, Double> asOfBar = chart.bars().floorEntry(asOf);
        if (asOfBar == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No trading session on or before " + asOf + " for " + symbol);
        }
        NavigableMap.Entry<LocalDate, Double> nowBar = chart.bars().lastEntry();
        double priceAsOf = asOfBar.getValue();
        double priceNowLive = chart.regularMarketPrice() != null && chart.regularMarketPrice() > 0
                ? chart.regularMarketPrice()
                : nowBar.getValue();
        LocalDate priceNowSession = nowBar.getKey();

        BigDecimal priceAsOfBd = money6(priceAsOf);
        BigDecimal priceNowBd = money6(priceNowLive);
        BigDecimal shares = invested.divide(priceAsOfBd, 8, RoundingMode.HALF_UP);
        BigDecimal worthNow = shares.multiply(priceNowBd).setScale(2, RoundingMode.HALF_UP);
        BigDecimal gainAmount = worthNow.subtract(invested).setScale(2, RoundingMode.HALF_UP);
        BigDecimal gainPercent = invested.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : gainAmount
                        .multiply(BigDecimal.valueOf(100))
                        .divide(invested, 4, RoundingMode.HALF_UP);

        String companyName = chart.companyName() == null || chart.companyName().isBlank()
                ? symbol
                : chart.companyName().trim();
        String detail = buildDetailAnswer(
                symbol,
                companyName,
                invested,
                asOf,
                asOfBar.getKey(),
                priceAsOfBd,
                shares,
                priceNowBd,
                priceNowSession,
                worthNow,
                gainAmount,
                gainPercent);

        boolean save = body != null && Boolean.TRUE.equals(body.save());
        if (!save) {
            return new InvestmentThenNowResultDto(
                    null,
                    symbol,
                    companyName,
                    invested,
                    asOf,
                    priceAsOfBd,
                    asOfBar.getKey(),
                    shares,
                    priceNowBd,
                    priceNowSession,
                    worthNow,
                    gainAmount,
                    gainPercent,
                    detail,
                    chart.source(),
                    Instant.now(),
                    null,
                    null,
                    false);
        }

        long owner = currentUser.requireUserId();
        FinanceInvestmentThenNow row = repository
                .findByOwnerUserIdAndSymbolAndAsOfDateAndInvestedAmount(owner, symbol, asOf, invested)
                .orElseGet(FinanceInvestmentThenNow::new);
        Instant now = Instant.now();
        row.setOwnerUserId(owner);
        row.setSymbol(symbol);
        row.setCompanyName(companyName);
        row.setInvestedAmount(invested);
        row.setAsOfDate(asOf);
        row.setPriceAsOfDate(priceAsOfBd);
        row.setPriceAsOfSession(asOfBar.getKey());
        row.setShares(shares);
        row.setPriceNow(priceNowBd);
        row.setPriceNowSession(priceNowSession);
        row.setWorthNow(worthNow);
        row.setGainAmount(gainAmount);
        row.setGainPercent(gainPercent);
        row.setDetailAnswer(detail);
        row.setPriceSource(chart.source());
        row.setComputedAt(now);
        row = repository.save(row);
        return toDto(row, true);
    }

    /**
     * Nasdaq chart first (no API key; works from datacenter IPs). Alpha Vantage compact daily is the
     * fallback when a key is configured. Yahoo is intentionally not used — it rate-limits server IPs.
     */
    private ChartSnapshot loadPrices(String symbol, LocalDate asOf, LocalDate today) {
        String cacheKey = symbol + "|" + asOf + "|" + today;
        CachedChart cached = chartCache.get(cacheKey);
        if (cached != null && cached.fetchedAt().plus(CHART_CACHE_TTL).isAfter(Instant.now())) {
            return cached.snapshot();
        }

        List<String> errors = new ArrayList<>();
        ChartSnapshot nasdaq = fetchNasdaqChart(symbol, asOf.minusDays(14), today, errors);
        if (nasdaq != null && !nasdaq.bars().isEmpty()) {
            chartCache.put(cacheKey, new CachedChart(Instant.now(), nasdaq));
            return nasdaq;
        }

        ChartSnapshot alpha = fetchAlphaVantageDaily(symbol, errors);
        if (alpha != null && !alpha.bars().isEmpty()) {
            chartCache.put(cacheKey, new CachedChart(Instant.now(), alpha));
            return alpha;
        }

        if (cached != null) {
            log.warn("then/now serving stale chart for {} after Nasdaq/AV failures: {}", symbol, errors);
            return cached.snapshot();
        }
        String detail = errors.isEmpty() ? "no price source returned data" : String.join("; ", errors);
        throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY, "Could not load price history for " + symbol + ": " + detail);
    }

    /** Nasdaq quote chart API — try stocks then ETF asset classes. */
    private ChartSnapshot fetchNasdaqChart(
            String symbol, LocalDate start, LocalDate end, List<String> errors) {
        String nasdaqSymbol = toNasdaqSymbol(symbol);
        for (String assetClass : NASDAQ_ASSET_CLASSES) {
            try {
                String url = String.format(
                        Locale.ROOT,
                        NASDAQ_CHART_URL,
                        URLEncoder.encode(nasdaqSymbol, StandardCharsets.UTF_8),
                        assetClass,
                        start.format(ISO_DAY),
                        end.format(ISO_DAY));
                int timeoutMs = Math.max(props.newsTimeoutMs(), 20_000);
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .GET()
                        .timeout(Duration.ofMillis(timeoutMs))
                        .header("Accept", "application/json")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .header("User-Agent", BROWSER_UA)
                        .header("Origin", "https://www.nasdaq.com")
                        .header("Referer", "https://www.nasdaq.com/")
                        .build();
                HttpResponse<String> resp =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                    errors.add("nasdaq/" + assetClass + " HTTP " + resp.statusCode());
                    continue;
                }
                ChartSnapshot snapshot = parseNasdaqChart(objectMapper.readTree(resp.body()), symbol);
                if (snapshot != null && !snapshot.bars().isEmpty()) {
                    log.info("then/now used Nasdaq {} chart for {}", assetClass, symbol);
                    return snapshot;
                }
                errors.add("nasdaq/" + assetClass + " empty");
            } catch (Exception e) {
                errors.add("nasdaq/" + assetClass + " " + e.getMessage());
                log.warn("Nasdaq chart failed for {} ({}): {}", symbol, assetClass, e.toString());
            }
        }
        return null;
    }

    private ChartSnapshot parseNasdaqChart(JsonNode root, String fallbackSymbol) {
        JsonNode data = root.path("data");
        if (!data.isObject()) {
            return null;
        }
        JsonNode chart = data.path("chart");
        if (!chart.isArray() || chart.isEmpty()) {
            return null;
        }
        NavigableMap<LocalDate, Double> bars = new TreeMap<>();
        for (JsonNode point : chart) {
            JsonNode z = point.path("z");
            String dateText = text(z.get("dateTime"));
            Double close = money(z.get("close"));
            if (close == null) {
                close = money(z.get("value"));
            }
            if (dateText.isBlank() || close == null || close <= 0) {
                continue;
            }
            try {
                bars.put(LocalDate.parse(dateText, NASDAQ_DAY), close);
            } catch (DateTimeParseException ignored) {
                // skip malformed points
            }
        }
        if (bars.isEmpty()) {
            return null;
        }
        String company = text(data.get("company"));
        if (company.isBlank()) {
            company = fallbackSymbol;
        }
        Double live = money(data.get("lastSalePrice"));
        return new ChartSnapshot(company, live, bars, "nasdaq-chart");
    }

    /**
     * Alpha Vantage TIME_SERIES_DAILY (free tier). Returns null when no key is configured or the call fails.
     * Gated on the key alone rather than {@code alphaVantageEnabled}, because that flag also starts the hourly
     * quote crawler, which would exhaust the free-tier daily quota. This call only runs on user request.
     */
    private ChartSnapshot fetchAlphaVantageDaily(String symbol, List<String> errors) {
        String key = props.alphaVantageApiKey();
        if (key == null || key.isBlank()) {
            errors.add("alpha-vantage not configured");
            return null;
        }
        try {
            String url = props.alphaVantageBaseUrl()
                    // "full" is premium-only; compact includes the latest 100 trading sessions,
                    // which covers the Then & now default/current-date use case on the free tier.
                    + "?function=TIME_SERIES_DAILY&outputsize=compact&symbol="
                    + URLEncoder.encode(symbol, StandardCharsets.UTF_8)
                    + "&apikey="
                    + URLEncoder.encode(key, StandardCharsets.UTF_8);
            int timeoutMs = Math.max(props.newsTimeoutMs(), 20_000);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Accept", "application/json")
                    .build();
            HttpResponse<String> resp =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                errors.add("alpha-vantage HTTP " + resp.statusCode());
                return null;
            }
            JsonNode payload = objectMapper.readTree(resp.body());
            JsonNode series = payload.path("Time Series (Daily)");
            if (!series.isObject()) {
                String notice = firstText(payload, "Information", "Note", "Error Message");
                errors.add("alpha-vantage " + (notice.isBlank() ? "no series" : notice));
                log.warn(
                        "Alpha Vantage returned no daily series for {}{}",
                        symbol,
                        notice.isBlank() ? "" : ": " + notice);
                return null;
            }
            NavigableMap<LocalDate, Double> bars = new TreeMap<>();
            Iterator<String> dates = series.fieldNames();
            while (dates.hasNext()) {
                String dateKey = dates.next();
                Double close = dbl(series.path(dateKey).get("4. close"));
                if (close == null || close <= 0) {
                    continue;
                }
                try {
                    bars.put(LocalDate.parse(dateKey), close);
                } catch (DateTimeParseException ignored) {
                    // skip malformed date keys
                }
            }
            if (bars.isEmpty()) {
                errors.add("alpha-vantage empty bars");
                return null;
            }
            log.info("then/now used Alpha Vantage daily for {}", symbol);
            return new ChartSnapshot(symbol, null, bars, "alpha-vantage-daily");
        } catch (Exception e) {
            errors.add("alpha-vantage " + e.getMessage());
            log.warn("Alpha Vantage fallback failed for {}: {}", symbol, e.toString());
            return null;
        }
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node.get(field));
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String buildDetailAnswer(
            String symbol,
            String companyName,
            BigDecimal invested,
            LocalDate requestedAsOf,
            LocalDate sessionAsOf,
            BigDecimal priceAsOf,
            BigDecimal shares,
            BigDecimal priceNow,
            LocalDate priceNowSession,
            BigDecimal worthNow,
            BigDecimal gainAmount,
            BigDecimal gainPercent) {
        String nameLabel = companyName.equalsIgnoreCase(symbol) ? symbol : companyName + " (" + symbol + ")";
        String asOfNote = sessionAsOf.equals(requestedAsOf)
                ? ""
                : String.format(
                        Locale.US,
                        " There was no trading session on %s, so the prior close from %s was used.",
                        requestedAsOf.format(LONG_DATE),
                        sessionAsOf.format(LONG_DATE));
        String direction = gainAmount.signum() >= 0 ? "gain" : "loss";
        return String.format(
                Locale.US,
                "If $%,.2f were invested in %s stocks on %s, it would be worth about $%,.2f now "
                        + "(as of the %s close). That is a %s of $%,.2f (%+.2f%%). "
                        + "At an adjusted close of $%,.4f on %s, that purchase would have bought about %,.4f shares; "
                        + "marked at $%,.4f today, those shares are worth $%,.2f.%s "
                        + "Prices use Nasdaq daily closes (educational estimate, not investment advice).",
                invested,
                nameLabel,
                requestedAsOf.format(LONG_DATE),
                worthNow,
                priceNowSession.format(LONG_DATE),
                direction,
                gainAmount.abs(),
                gainPercent,
                priceAsOf,
                sessionAsOf.format(LONG_DATE),
                shares,
                priceNow,
                worthNow,
                asOfNote);
    }

    private static InvestmentThenNowResultDto toDto(FinanceInvestmentThenNow row, boolean saved) {
        return new InvestmentThenNowResultDto(
                row.getId(),
                row.getSymbol(),
                row.getCompanyName(),
                row.getInvestedAmount(),
                row.getAsOfDate(),
                row.getPriceAsOfDate(),
                row.getPriceAsOfSession(),
                row.getShares(),
                row.getPriceNow(),
                row.getPriceNowSession(),
                row.getWorthNow(),
                row.getGainAmount(),
                row.getGainPercent(),
                row.getDetailAnswer(),
                row.getPriceSource(),
                row.getComputedAt(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                saved);
    }

    private static String normalizeSymbol(String symbol) {
        if (symbol == null) {
            return "";
        }
        return symbol.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9.\\-^=]", "");
    }

    /** Nasdaq prefers BRK.B over BRK-B. */
    private static String toNasdaqSymbol(String symbol) {
        return symbol.replace('-', '.');
    }

    private static BigDecimal money6(double v) {
        return BigDecimal.valueOf(v).setScale(6, RoundingMode.HALF_UP);
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
            double v = n.asDouble();
            return Double.isFinite(v) ? v : null;
        }
        return money(n);
    }

    /** Parse \"$738.93\" / \"738.93\" / numeric JSON nodes. */
    private static Double money(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isNumber()) {
            double v = n.asDouble();
            return Double.isFinite(v) && v > 0 ? v : null;
        }
        if (!n.isTextual()) {
            return null;
        }
        String raw = n.asText("").trim();
        if (raw.isEmpty() || "N/A".equalsIgnoreCase(raw)) {
            return null;
        }
        String cleaned = raw.replace("$", "").replace(",", "").trim();
        try {
            double v = Double.parseDouble(cleaned);
            return Double.isFinite(v) && v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record ChartSnapshot(
            String companyName, Double regularMarketPrice, NavigableMap<LocalDate, Double> bars, String source) {}

    private record CachedChart(Instant fetchedAt, ChartSnapshot snapshot) {}
}
