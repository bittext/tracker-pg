package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.finance.dto.CompanyEarningsEventDto;
import com.svp.tracker.finance.dto.CompanyEarningsHistoryRowDto;
import com.svp.tracker.finance.dto.CompanyQuoteSnapshotDto;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Live earnings calendar + company quote/history from Nasdaq public JSON APIs. */
@Service
@Slf4j
public class NasdaqEarningsService {

    private static final ZoneId EASTERN = ZoneId.of("America/New_York");
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration DAY_CACHE_TTL = Duration.ofMinutes(15);
    private static final Duration QUOTE_CACHE_TTL = Duration.ofMinutes(5);
    private static final Pattern DIGITS = Pattern.compile("[^0-9]");
    private static final DateTimeFormatter EARNINGS_MSG_DATE =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
    private final ConcurrentHashMap<LocalDate, CachedDay> dayCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedQuote> quoteCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedHistory> historyCache = new ConcurrentHashMap<>();

    public List<CompanyEarningsEventDto> calendar(LocalDate from, int days) {
        LocalDate start = from != null ? from : LocalDate.now(EASTERN);
        int span = Math.max(1, Math.min(days, 31));
        List<CompanyEarningsEventDto> out = new ArrayList<>();
        for (int i = 0; i < span; i++) {
            LocalDate day = start.plusDays(i);
            out.addAll(eventsForDay(day));
        }
        out.sort(Comparator.comparing(CompanyEarningsEventDto::reportDate)
                .thenComparing(e -> e.marketCapValue() == null ? 0L : -e.marketCapValue())
                .thenComparing(CompanyEarningsEventDto::symbol));
        return out;
    }

    public CompanyQuoteSnapshotDto quote(String symbol) {
        String sym = normalizeSymbol(symbol);
        if (sym.isBlank()) {
            return null;
        }
        CachedQuote cached = quoteCache.get(sym);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.quote();
        }
        try {
            JsonNode root = getJson("https://api.nasdaq.com/api/quote/" + sym + "/info?assetclass=stocks");
            JsonNode data = root != null ? root.path("data") : null;
            if (data == null || data.isMissingNode() || data.isNull()) {
                return null;
            }
            JsonNode primary = data.path("primaryData");
            JsonNode keyStats = data.path("keyStats");
            String upcoming = null;
            JsonNode notifications = data.path("notifications");
            if (notifications.isArray()) {
                for (JsonNode n : notifications) {
                    JsonNode types = n.path("eventTypes");
                    if (!types.isArray()) {
                        continue;
                    }
                    for (JsonNode t : types) {
                        if ("Earnings Date".equalsIgnoreCase(text(t, "eventName"))) {
                            upcoming = text(t, "message");
                            break;
                        }
                    }
                    if (upcoming != null) {
                        break;
                    }
                }
            }
            CompanyQuoteSnapshotDto quote = new CompanyQuoteSnapshotDto(
                    text(data, "symbol").isBlank() ? sym : text(data, "symbol"),
                    text(data, "companyName"),
                    text(primary, "lastSalePrice"),
                    text(primary, "netChange"),
                    text(primary, "percentageChange"),
                    text(primary, "deltaIndicator"),
                    text(data, "exchange"),
                    text(data, "marketStatus"),
                    text(keyStats.path("fiftyTwoWeekHighLow"), "value"),
                    upcoming);
            quoteCache.put(sym, new CachedQuote(quote, Instant.now().plus(QUOTE_CACHE_TTL)));
            return quote;
        } catch (Exception e) {
            log.warn("Nasdaq quote failed for {}: {}", sym, e.toString());
            return cached != null ? cached.quote() : null;
        }
    }

    public List<CompanyEarningsHistoryRowDto> earningsHistory(String symbol) {
        String sym = normalizeSymbol(symbol);
        if (sym.isBlank()) {
            return List.of();
        }
        CachedHistory cached = historyCache.get(sym);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.rows();
        }
        try {
            JsonNode root = getJson("https://api.nasdaq.com/api/company/" + sym.toLowerCase(Locale.ROOT) + "/earnings-surprise");
            JsonNode rows = root != null
                    ? root.path("data").path("earningsSurpriseTable").path("rows")
                    : null;
            List<CompanyEarningsHistoryRowDto> out = new ArrayList<>();
            if (rows != null && rows.isArray()) {
                for (JsonNode row : rows) {
                    out.add(new CompanyEarningsHistoryRowDto(
                            text(row, "fiscalQtrEnd"),
                            text(row, "dateReported"),
                            stringify(row.get("eps")),
                            text(row, "consensusForecast"),
                            text(row, "percentageSurprise")));
                }
            }
            historyCache.put(sym, new CachedHistory(List.copyOf(out), Instant.now().plus(QUOTE_CACHE_TTL)));
            return out;
        } catch (Exception e) {
            log.warn("Nasdaq earnings history failed for {}: {}", sym, e.toString());
            return cached != null ? cached.rows() : List.of();
        }
    }

    /** Best-effort parse of "Earnings Date : Jul 30, 2026" from quote notifications. */
    public LocalDate parseUpcomingEarningsDate(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        int colon = message.lastIndexOf(':');
        String datePart = colon >= 0 ? message.substring(colon + 1).trim() : message.trim();
        try {
            return LocalDate.parse(datePart, EARNINGS_MSG_DATE);
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<CompanyEarningsEventDto> eventsForDay(LocalDate day) {
        CachedDay cached = dayCache.get(day);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.events();
        }
        try {
            JsonNode root = getJson("https://api.nasdaq.com/api/calendar/earnings?date=" + day);
            JsonNode rows = root != null ? root.path("data").path("rows") : null;
            List<CompanyEarningsEventDto> events = new ArrayList<>();
            if (rows != null && rows.isArray()) {
                for (JsonNode row : rows) {
                    String symbol = normalizeSymbol(text(row, "symbol"));
                    if (symbol.isBlank()) {
                        continue;
                    }
                    String marketCap = text(row, "marketCap");
                    events.add(new CompanyEarningsEventDto(
                            day,
                            symbol,
                            text(row, "name"),
                            marketCap,
                            parseMarketCap(marketCap),
                            text(row, "fiscalQuarterEnding"),
                            text(row, "epsForecast"),
                            text(row, "lastYearEPS"),
                            text(row, "lastYearRptDt"),
                            mapTiming(text(row, "time")),
                            false,
                            null));
                }
            }
            List<CompanyEarningsEventDto> frozen = List.copyOf(events);
            dayCache.put(day, new CachedDay(frozen, Instant.now().plus(DAY_CACHE_TTL)));
            return frozen;
        } catch (Exception e) {
            log.warn("Nasdaq earnings calendar failed for {}: {}", day, e.toString());
            return cached != null ? cached.events() : List.of();
        }
    }

    private JsonNode getJson(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(HTTP_TIMEOUT)
                .header("User-Agent", "Mozilla/5.0 (compatible; tracker-pg/1.0)")
                .header("Accept", "application/json, text/plain, */*")
                .GET()
                .build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + res.statusCode() + " for " + url);
        }
        return objectMapper.readTree(res.body());
    }

    static String normalizeSymbol(String symbol) {
        if (symbol == null) {
            return "";
        }
        return symbol.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9.\\-]", "");
    }

    static Long parseMarketCap(String raw) {
        if (raw == null || raw.isBlank() || "N/A".equalsIgnoreCase(raw)) {
            return null;
        }
        String digits = DIGITS.matcher(raw).replaceAll("");
        if (digits.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String mapTiming(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unspecified";
        }
        String t = raw.toLowerCase(Locale.ROOT);
        if (t.contains("pre")) {
            return "pre-market";
        }
        if (t.contains("after") || t.contains("post")) {
            return "after-close";
        }
        if (t.contains("not")) {
            return "unspecified";
        }
        return raw;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        return stringify(node.get(field));
    }

    private static String stringify(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        String v = node.asText();
        return v == null ? "" : v.trim();
    }

    private record CachedDay(List<CompanyEarningsEventDto> events, Instant expiresAt) {}

    private record CachedQuote(CompanyQuoteSnapshotDto quote, Instant expiresAt) {}

    private record CachedHistory(List<CompanyEarningsHistoryRowDto> rows, Instant expiresAt) {}
}
