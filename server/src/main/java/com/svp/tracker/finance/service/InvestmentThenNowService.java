package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.finance.domain.FinanceInvestmentThenNow;
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
 * Answers “$X invested on date in SYMBOL — worth now?” using Yahoo daily adjusted closes, and can persist
 * the answer per user for reference.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvestmentThenNowService {

    private static final ZoneId NY = ZoneId.of("America/New_York");
    private static final String YAHOO_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/122.0.0.0 Safari/537.36";
    /** Yahoo throttles per host, so a 429 on query1 often succeeds on query2. */
    private static final List<String> CHART_HOSTS =
            List.of("https://query1.finance.yahoo.com", "https://query2.finance.yahoo.com");
    private static final String CHART_PATH =
            "/v8/finance/chart/%s?period1=%d&period2=%d&interval=1d&includeAdjustedClose=true";
    private static final BigDecimal DEFAULT_INVESTED = new BigDecimal("78198.72");
    private static final LocalDate DEFAULT_AS_OF = LocalDate.of(2026, 6, 28);
    private static final DateTimeFormatter LONG_DATE =
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US);
    private static final Duration CHART_CACHE_TTL = Duration.ofMinutes(10);

    private final FinanceProperties props;
    private final CurrentUserService currentUser;
    private final FinanceInvestmentThenNowRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, CachedChart> chartCache = new ConcurrentHashMap<>();

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
     * Yahoo first; when it is rate limiting (HTTP 429) fall back to Alpha Vantage if a key is configured.
     * Yahoo throttles aggressively for datacenter IPs, so a single source is not reliable enough.
     */
    private ChartSnapshot loadPrices(String symbol, LocalDate asOf, LocalDate today) {
        try {
            return fetchChart(symbol, asOf.minusDays(14), today.plusDays(1));
        } catch (ResponseStatusException yahooError) {
            ChartSnapshot fallback = fetchAlphaVantageDaily(symbol);
            if (fallback != null && !fallback.bars().isEmpty()) {
                log.info("then/now used Alpha Vantage fallback for {} after Yahoo {}", symbol, yahooError.getStatusCode());
                return fallback;
            }
            throw yahooError;
        }
    }

    /**
     * Alpha Vantage TIME_SERIES_DAILY (free tier). Returns null when no key is configured or the call fails.
     * Gated on the key alone rather than {@code alphaVantageEnabled}, because that flag also starts the hourly
     * quote crawler, which would exhaust the free-tier daily quota. This call only runs on user request.
     */
    private ChartSnapshot fetchAlphaVantageDaily(String symbol) {
        String key = props.alphaVantageApiKey();
        if (key == null || key.isBlank()) {
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
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeoutMs))
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Accept", "application/json")
                    .build();
            HttpResponse<String> resp =
                    client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.warn("Alpha Vantage daily HTTP {} for {}", resp.statusCode(), symbol);
                return null;
            }
            JsonNode payload = objectMapper.readTree(resp.body());
            JsonNode series = payload.path("Time Series (Daily)");
            if (!series.isObject()) {
                // Alpha Vantage answers 200 with an "Information"/"Note"/"Error Message" body for a bad key
                // or an exhausted free-tier quota; log it so the cause is visible.
                String notice = firstText(payload, "Information", "Note", "Error Message");
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
                return null;
            }
            return new ChartSnapshot(symbol, null, bars, "alpha-vantage-daily");
        } catch (Exception e) {
            log.warn("Alpha Vantage fallback failed for {}: {}", symbol, e.toString());
            return null;
        }
    }

    private ChartSnapshot fetchChart(String symbol, LocalDate start, LocalDate end) {
        String cacheKey = symbol + "|" + start + "|" + end;
        CachedChart cached = chartCache.get(cacheKey);
        if (cached != null && cached.fetchedAt().plus(CHART_CACHE_TTL).isAfter(Instant.now())) {
            return cached.snapshot();
        }

        long period1 = start.atStartOfDay(NY).toEpochSecond();
        long period2 = end.atStartOfDay(NY).toEpochSecond();
        String enc = URLEncoder.encode(symbol, StandardCharsets.UTF_8).replace("+", "%20");
        int timeoutMs = Math.max(props.newsTimeoutMs(), 20_000);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();

        int lastStatus = 0;
        Exception lastError = null;
        // Alternate hosts and retry: Yahoo returns 429 sporadically for server clients.
        for (int attempt = 0; attempt < CHART_HOSTS.size() * 2; attempt++) {
            String host = CHART_HOSTS.get(attempt % CHART_HOSTS.size());
            String url = host + String.format(Locale.ROOT, CHART_PATH, enc, period1, period2);
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .GET()
                        .timeout(Duration.ofMillis(timeoutMs))
                        .header("Accept", "application/json")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .header("User-Agent", YAHOO_USER_AGENT)
                        .build();
                HttpResponse<String> resp =
                        client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    ChartSnapshot snapshot = parseChart(objectMapper.readTree(resp.body()));
                    if (!snapshot.bars().isEmpty()) {
                        chartCache.put(cacheKey, new CachedChart(Instant.now(), snapshot));
                    }
                    return snapshot;
                }
                lastStatus = resp.statusCode();
                if (resp.statusCode() != 429 && resp.statusCode() < 500) {
                    break;
                }
            } catch (Exception e) {
                lastError = e;
                log.warn("then/now chart attempt {} failed for {}: {}", attempt + 1, symbol, e.toString());
            }
            sleepBackoff(attempt);
        }

        // Serve a stale cache entry rather than failing the request outright.
        if (cached != null) {
            log.warn("then/now serving stale chart for {} after HTTP {}", symbol, lastStatus);
            return cached.snapshot();
        }
        if (lastStatus == 429) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Yahoo Finance is rate limiting price requests (HTTP 429). Wait a minute and try again.");
        }
        if (lastStatus > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Yahoo chart HTTP " + lastStatus + " for " + symbol);
        }
        String detail = lastError == null ? "no response" : lastError.getMessage();
        throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY, "Could not load price history for " + symbol + ": " + detail);
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

    private static void sleepBackoff(int attempt) {
        try {
            Thread.sleep(Math.min(1500L, 300L * (attempt + 1)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static ChartSnapshot parseChart(JsonNode root) {
        NavigableMap<LocalDate, Double> bars = new TreeMap<>();
        JsonNode results = root.path("chart").path("result");
        if (!results.isArray() || results.isEmpty()) {
            return new ChartSnapshot("", null, bars, "yahoo-chart");
        }
        JsonNode r0 = results.get(0);
        JsonNode meta = r0.path("meta");
        String shortName = text(meta.get("shortName"));
        String longName = text(meta.get("longName"));
        String company = !shortName.isBlank() ? shortName : longName;
        Double live = dbl(meta.get("regularMarketPrice"));

        JsonNode ts = r0.path("timestamp");
        JsonNode adj = r0.path("indicators").path("adjclose").path(0).path("adjclose");
        JsonNode raw = r0.path("indicators").path("quote").path(0).path("close");
        for (int i = 0; i < ts.size(); i++) {
            JsonNode t = ts.get(i);
            JsonNode p = i < adj.size() ? adj.get(i) : null;
            if (p == null || !p.isNumber()) {
                p = i < raw.size() ? raw.get(i) : null;
            }
            if (t == null || !t.isNumber() || p == null || !p.isNumber()) {
                continue;
            }
            double px = p.asDouble();
            if (!Double.isFinite(px) || px <= 0) {
                continue;
            }
            LocalDate date = Instant.ofEpochSecond(t.asLong()).atZone(NY).toLocalDate();
            bars.put(date, px);
        }
        return new ChartSnapshot(company, live, bars, "yahoo-chart");
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
                        + "Prices are Yahoo Finance daily adjusted closes (educational estimate, not investment advice).",
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
        if (n == null || n.isNull() || !n.isNumber()) {
            return null;
        }
        double v = n.asDouble();
        return Double.isFinite(v) ? v : null;
    }

    private record ChartSnapshot(
            String companyName, Double regularMarketPrice, NavigableMap<LocalDate, Double> bars, String source) {}

    private record CachedChart(Instant fetchedAt, ChartSnapshot snapshot) {}
}
