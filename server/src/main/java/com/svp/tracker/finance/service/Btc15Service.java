package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.finance.dto.Btc15DeskDto;
import com.svp.tracker.finance.dto.Btc15HourDto;
import com.svp.tracker.finance.dto.Btc15WindowDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** BTC 15-minute contracts: stated target, Coinbase at the open, and the settlement print. */
@Service
@Slf4j
public class Btc15Service {

    static final ZoneId CENTRAL = ZoneId.of("America/Chicago");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("h a", Locale.US);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("h:mm", Locale.US);
    private static final DateTimeFormatter CLOCK_MERIDIEM = DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public Btc15DeskDto load(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now(CENTRAL) : date;
        ZonedDateTime from = day.atStartOfDay(CENTRAL);
        ZonedDateTime to = day.plusDays(1).atStartOfDay(CENTRAL);
        List<JsonNode> markets = fetchMarkets(from.toInstant(), to.toInstant());
        Map<Long, double[]> candles = fetchCandles(from.toInstant(), to.toInstant());
        List<Btc15WindowDto> windows = new ArrayList<>();
        for (JsonNode market : markets) {
            Btc15WindowDto window = toWindow(market, candles);
            if (window != null) {
                windows.add(window);
            }
        }
        windows.sort(Comparator.comparing(Btc15WindowDto::windowStart));
        Map<String, List<Btc15WindowDto>> byHour = new LinkedHashMap<>();
        for (Btc15WindowDto window : windows) {
            String hour = HOUR.format(window.windowStart().atZone(CENTRAL));
            byHour.computeIfAbsent(hour, key -> new ArrayList<>()).add(window);
        }
        List<Btc15HourDto> hours = new ArrayList<>();
        int settled = 0;
        int above = 0;
        for (Map.Entry<String, List<Btc15WindowDto>> entry : byHour.entrySet()) {
            int hourSettled = 0;
            int hourAbove = 0;
            for (Btc15WindowDto window : entry.getValue()) {
                if (window.aboveTarget() == null) {
                    continue;
                }
                hourSettled++;
                if (window.aboveTarget()) {
                    hourAbove++;
                }
            }
            settled += hourSettled;
            above += hourAbove;
            hours.add(new Btc15HourDto(entry.getKey(), hourSettled, hourAbove, rate(hourAbove, hourSettled), entry.getValue()));
        }
        return new Btc15DeskDto(day, settled, above, rate(above, settled), hours);
    }

    private Btc15WindowDto toWindow(JsonNode market, Map<Long, double[]> candles) {
        Instant start = instant(market.path("open_time").asText(""));
        Instant end = instant(market.path("close_time").asText(""));
        BigDecimal target = decimal(market.get("floor_strike"));
        if (start == null || end == null || target == null) {
            return null;
        }
        BigDecimal startPrice = candleOpen(candles, start);
        BigDecimal endPrice = decimal(market.get("expiration_value"));
        if (endPrice == null) {
            endPrice = candleOpen(candles, end);
        }
        Boolean above = aboveTarget(market.path("result").asText(""), endPrice, target);
        ZonedDateTime startLocal = start.atZone(CENTRAL);
        ZonedDateTime endLocal = end.atZone(CENTRAL);
        String label = CLOCK.format(startLocal) + "–" + CLOCK_MERIDIEM.format(endLocal);
        return new Btc15WindowDto(
                market.path("ticker").asText(""),
                label,
                start,
                end,
                target,
                startPrice,
                endPrice,
                gap(startPrice, target),
                gap(endPrice, target),
                above);
    }

    private static Boolean aboveTarget(String result, BigDecimal endPrice, BigDecimal target) {
        if ("yes".equalsIgnoreCase(result)) {
            return true;
        }
        if ("no".equalsIgnoreCase(result)) {
            return false;
        }
        if (endPrice == null || target == null) {
            return null;
        }
        return endPrice.compareTo(target) >= 0;
    }

    private static BigDecimal gap(BigDecimal price, BigDecimal target) {
        if (price == null || target == null) {
            return null;
        }
        return price.subtract(target).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal rate(int above, int settled) {
        if (settled == 0) {
            return null;
        }
        return BigDecimal.valueOf(above).divide(BigDecimal.valueOf(settled), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal candleOpen(Map<Long, double[]> candles, Instant at) {
        long minute = at.getEpochSecond() / 60 * 60;
        double[] candle = candles.get(minute);
        if (candle == null) {
            return null;
        }
        return BigDecimal.valueOf(candle[0]).setScale(2, RoundingMode.HALF_UP);
    }

    private List<JsonNode> fetchMarkets(Instant from, Instant to) {
        List<JsonNode> matched = new ArrayList<>();
        String cursor = "";
        for (int page = 0; page < 8; page++) {
            String url = "https://api.elections.kalshi.com/trade-api/v2/markets?series_ticker=KXBTC15M&limit=200";
            if (!cursor.isBlank()) {
                url += "&cursor=" + cursor;
            }
            JsonNode root = getJson(url);
            JsonNode markets = root.path("markets");
            if (!markets.isArray() || markets.isEmpty()) {
                break;
            }
            boolean olderThanDay = false;
            for (JsonNode market : markets) {
                Instant open = instant(market.path("open_time").asText(""));
                if (open == null) {
                    continue;
                }
                if (open.isBefore(from)) {
                    olderThanDay = true;
                    continue;
                }
                if (!open.isBefore(to)) {
                    continue;
                }
                matched.add(market);
            }
            cursor = root.path("cursor").asText("");
            if (olderThanDay || cursor.isBlank()) {
                break;
            }
        }
        return matched;
    }

    private Map<Long, double[]> fetchCandles(Instant from, Instant to) {
        Map<Long, double[]> candles = new HashMap<>();
        Instant cursor = from.minus(Duration.ofMinutes(1));
        Instant end = to.plus(Duration.ofMinutes(2));
        Instant latest = Instant.now().plus(Duration.ofMinutes(1));
        if (end.isAfter(latest)) {
            end = latest;
        }
        while (cursor.isBefore(end)) {
            Instant next = cursor.plus(Duration.ofMinutes(250));
            if (next.isAfter(end)) {
                next = end;
            }
            String url = "https://api.exchange.coinbase.com/products/BTC-USD/candles?granularity=60&start="
                    + cursor + "&end=" + next;
            JsonNode root = getJson(url);
            if (root.isArray()) {
                for (JsonNode candle : root) {
                    if (!candle.isArray() || candle.size() < 5) {
                        continue;
                    }
                    long time = candle.get(0).asLong();
                    candles.put(time, new double[] {candle.get(3).asDouble(), candle.get(4).asDouble()});
                }
            }
            cursor = next;
        }
        return candles;
    }

    private JsonNode getJson(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("User-Agent", "tracker-pg/btc-15")
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("BTC 15-minute feed returned HTTP " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("BTC 15-minute feed could not be read", ex);
        }
    }

    private static Instant instant(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (Exception ex) {
            return null;
        }
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String text = node.asText("").trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
