package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.finance.dto.RobinhoodRhDailyBenchmarkPointDto;
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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Daily S&P 500 adjusted closes used to benchmark Daily Tracker account values. */
@Service
@Slf4j
public class Sp500DailyBenchmarkService {

    private static final String SYMBOL = "^GSPC";
    private static final String CHART_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart/%s"
                    + "?period1=%d&period2=%d&interval=1d&includeAdjustedClose=true";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final Duration CACHE_TTL = Duration.ofHours(6);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
    private final Map<Integer, CachedYear> yearCache = new ConcurrentHashMap<>();

    public List<RobinhoodRhDailyBenchmarkPointDto> alignedCloses(Set<LocalDate> snapshotDates) {
        if (snapshotDates == null || snapshotDates.isEmpty()) {
            return List.of();
        }

        Map<Integer, TreeMap<LocalDate, BigDecimal>> closesByYear = new TreeMap<>();
        for (LocalDate date : snapshotDates) {
            closesByYear.computeIfAbsent(date.getYear(), this::closesForYear);
        }

        List<RobinhoodRhDailyBenchmarkPointDto> points = new ArrayList<>();
        snapshotDates.stream().sorted().forEach(snapshotDate -> {
            TreeMap<LocalDate, BigDecimal> closes = closesByYear.get(snapshotDate.getYear());
            Map.Entry<LocalDate, BigDecimal> close = closes == null ? null : closes.floorEntry(snapshotDate);
            if (close != null && !close.getKey().isBefore(snapshotDate.minusDays(7))) {
                points.add(new RobinhoodRhDailyBenchmarkPointDto(
                        snapshotDate, close.getKey(), close.getValue()));
            }
        });
        return List.copyOf(points);
    }

    private TreeMap<LocalDate, BigDecimal> closesForYear(int year) {
        CachedYear cached = yearCache.get(year);
        if (cached != null && cached.fetchedAt().plus(CACHE_TTL).isAfter(Instant.now())) {
            return cached.closes();
        }
        try {
            TreeMap<LocalDate, BigDecimal> closes = fetchYear(year);
            if (!closes.isEmpty()) {
                CachedYear fresh = new CachedYear(Instant.now(), closes);
                yearCache.put(year, fresh);
                return fresh.closes();
            }
        } catch (Exception e) {
            log.warn("S&P 500 benchmark fetch failed for {}", year, e);
        }
        return cached != null ? cached.closes() : new TreeMap<>();
    }

    private TreeMap<LocalDate, BigDecimal> fetchYear(int year) throws Exception {
        LocalDate start = LocalDate.of(year, 1, 1).minusDays(10);
        LocalDate end = LocalDate.of(year + 1, 1, 1).plusDays(1);
        long period1 = start.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        long period2 = end.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        String encoded = URLEncoder.encode(SYMBOL, StandardCharsets.UTF_8).replace("+", "%20");
        String url = String.format(Locale.ROOT, CHART_URL, encoded, period1, period2);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .build();
        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Yahoo chart HTTP " + response.statusCode());
        }
        return parseCloses(objectMapper.readTree(response.body()));
    }

    static TreeMap<LocalDate, BigDecimal> parseCloses(JsonNode root) {
        TreeMap<LocalDate, BigDecimal> closes = new TreeMap<>();
        JsonNode results = root.path("chart").path("result");
        if (!results.isArray() || results.isEmpty()) {
            return closes;
        }
        JsonNode result = results.get(0);
        JsonNode timestamps = result.path("timestamp");
        JsonNode adjusted =
                result.path("indicators").path("adjclose").path(0).path("adjclose");
        JsonNode raw = result.path("indicators").path("quote").path(0).path("close");
        for (int i = 0; i < timestamps.size(); i++) {
            JsonNode timestamp = timestamps.get(i);
            JsonNode price = i < adjusted.size() ? adjusted.get(i) : null;
            if (price == null || !price.isNumber()) {
                price = i < raw.size() ? raw.get(i) : null;
            }
            if (timestamp == null || !timestamp.isNumber() || price == null || !price.isNumber()) {
                continue;
            }
            double value = price.asDouble();
            if (!Double.isFinite(value) || value <= 0) {
                continue;
            }
            LocalDate date = Instant.ofEpochSecond(timestamp.asLong()).atZone(NEW_YORK).toLocalDate();
            closes.put(date, BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP));
        }
        return closes;
    }

    private record CachedYear(Instant fetchedAt, TreeMap<LocalDate, BigDecimal> closes) {}
}
