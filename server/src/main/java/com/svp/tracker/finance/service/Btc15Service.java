package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.finance.dto.Btc15DeskDto;
import com.svp.tracker.finance.dto.Btc15HourDto;
import com.svp.tracker.finance.dto.Btc15SharpDto;
import com.svp.tracker.finance.dto.Btc15StreakDayDto;
import com.svp.tracker.finance.dto.Btc15StreakDeskDto;
import com.svp.tracker.finance.dto.Btc15StreakRunDto;
import com.svp.tracker.finance.dto.Btc15WindowDto;
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
import java.time.temporal.ChronoUnit;
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
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("EEE MMM d", Locale.US);
    /** A last-5-minute move of this many dollars counts as a sharp turn. */
    private static final BigDecimal SHARP_MOVE = new BigDecimal("75");

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
        List<Btc15SharpDto> sharps = sharpTurns(candles, from, to.toInstant());
        int sharpUp = 0;
        int sharpDown = 0;
        for (Btc15SharpDto sharp : sharps) {
            if ("up".equals(sharp.direction())) {
                sharpUp++;
            } else {
                sharpDown++;
            }
        }
        return new Btc15DeskDto(day, settled, above, rate(above, settled), hours, sharpUp, sharpDown, SHARP_MOVE, sharps);
    }

    /**
     * Last five minutes of each clock 15-minute window. A sharp turn is a move of at least $75 in those five minutes.
     */
    private List<Btc15SharpDto> sharpTurns(Map<Long, double[]> candles, ZonedDateTime from, Instant dayEnd) {
        List<Btc15SharpDto> sharps = new ArrayList<>();
        Instant now = Instant.now();
        Instant limit = dayEnd.isBefore(now) ? dayEnd : now;
        for (ZonedDateTime start = from; start.toInstant().isBefore(limit); start = start.plusMinutes(15)) {
            Instant open = start.toInstant();
            Instant mark = open.plus(Duration.ofMinutes(10));
            Instant close = open.plus(Duration.ofMinutes(15));
            if (mark.isAfter(limit)) {
                break;
            }
            BigDecimal atOpen = candleOpen(candles, open);
            BigDecimal atMark = candleOpen(candles, mark);
            BigDecimal atClose = close.isAfter(limit) ? latestClose(candles, mark, limit) : candleOpen(candles, close);
            if (atClose == null) {
                atClose = candleClose(candles, close.minus(Duration.ofMinutes(1)));
            }
            if (atOpen == null || atMark == null || atClose == null) {
                continue;
            }
            BigDecimal firstTen = atMark.subtract(atOpen).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lastFive = atClose.subtract(atMark).setScale(2, RoundingMode.HALF_UP);
            if (lastFive.abs().compareTo(SHARP_MOVE) < 0) {
                continue;
            }
            boolean up = lastFive.signum() > 0;
            boolean reversed = firstTen.signum() != 0 && firstTen.signum() != lastFive.signum();
            String label = CLOCK.format(start) + "–" + CLOCK_MERIDIEM.format(start.plusMinutes(15));
            sharps.add(new Btc15SharpDto(label, up ? "up" : "down", lastFive, firstTen, reversed));
        }
        return sharps;
    }

    private BigDecimal candleClose(Map<Long, double[]> candles, Instant at) {
        long minute = at.getEpochSecond() / 60 * 60;
        double[] candle = candles.get(minute);
        if (candle == null || candle.length < 2) {
            return null;
        }
        return BigDecimal.valueOf(candle[1]).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal latestClose(Map<Long, double[]> candles, Instant from, Instant to) {
        BigDecimal close = null;
        for (long minute = from.getEpochSecond() / 60 * 60; minute <= to.getEpochSecond(); minute += 60) {
            double[] candle = candles.get(minute);
            if (candle != null && candle.length >= 2) {
                close = BigDecimal.valueOf(candle[1]).setScale(2, RoundingMode.HALF_UP);
            }
        }
        return close;
    }

    private Btc15WindowDto toWindow(JsonNode market, Map<Long, double[]> candles) {
        Instant start = instant(market.path("open_time").asText(""));
        Instant end = instant(market.path("close_time").asText(""));
        BigDecimal target = decimal(market.get("floor_strike"));
        if (start == null || end == null || target == null) {
            return null;
        }
        BigDecimal startPrice = candleOpen(candles, start);
        String result = market.path("result").asText("");
        BigDecimal expiration = decimal(market.get("expiration_value"));
        boolean hasKalshiResult = "yes".equalsIgnoreCase(result) || "no".equalsIgnoreCase(result);
        boolean settled = hasKalshiResult || expiration != null;

        BigDecimal endPrice = expiration;
        Boolean above = null;
        if (hasKalshiResult) {
            above = "yes".equalsIgnoreCase(result);
        } else if (expiration != null) {
            above = expiration.compareTo(target) >= 0;
        }

        BigDecimal currentPrice = null;
        BigDecimal currentGap = null;
        Boolean liveAbove = null;
        if (!settled) {
            Instant now = Instant.now();
            Instant liveTo = now.isBefore(end) ? now : end;
            currentPrice = latestClose(candles, start, liveTo);
            if (currentPrice == null) {
                currentPrice = candleClose(candles, liveTo);
            }
            if (currentPrice == null) {
                currentPrice = startPrice;
            }
            currentGap = gap(currentPrice, target);
            if (currentPrice != null) {
                liveAbove = currentPrice.compareTo(target) >= 0;
            }
        }

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
                above,
                currentPrice,
                currentGap,
                liveAbove,
                !settled);
    }

    private static BigDecimal gap(BigDecimal price, BigDecimal target) {
        if (price == null || target == null) {
            return null;
        }
        return price.subtract(target).setScale(2, RoundingMode.HALF_UP);
    }

    /** Settled outcome only: Kalshi yes/no, else expiration vs target. */
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

    public Btc15StreakDeskDto streaks(String seriesRaw, LocalDate from, LocalDate to) {
        String series = seriesOrDefault(seriesRaw);
        LocalDate end = to == null ? LocalDate.now(CENTRAL) : to;
        LocalDate start = from == null ? end.minusDays(6) : from;
        if (start.isAfter(end)) {
            LocalDate swap = start;
            start = end;
            end = swap;
        }
        if (ChronoUnit.DAYS.between(start, end) > 30) {
            start = end.minusDays(30);
        }
        List<SettledWindow> windows = settledWindows(series, start, end);
        List<int[]> runs = new ArrayList<>();
        int current = 0;
        int runStart = 0;
        int noWithNext = 0;
        int noThenNo = 0;
        for (int i = 0; i < windows.size(); i++) {
            boolean miss = !windows.get(i).above;
            if (i + 1 < windows.size() && miss) {
                noWithNext++;
                if (!windows.get(i + 1).above) {
                    noThenNo++;
                }
            }
            if (miss) {
                if (current == 0) {
                    runStart = i;
                }
                current++;
            } else if (current > 0) {
                runs.add(new int[] {current, runStart, i - 1});
                current = 0;
            }
        }
        if (current > 0) {
            runs.add(new int[] {current, runStart, windows.size() - 1});
        }
        return streakDesk(series, start, end, windows, runs, noWithNext, noThenNo);
    }

    private Btc15StreakDeskDto streakDesk(
            String series,
            LocalDate start,
            LocalDate end,
            List<SettledWindow> windows,
            List<int[]> runs,
            int noWithNext,
            int noThenNo) {
        Map<LocalDate, int[]> days = new LinkedHashMap<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            days.put(day, new int[11]);
        }
        int below = 0;
        for (SettledWindow window : windows) {
            int[] bucket = days.computeIfAbsent(window.start.atZone(CENTRAL).toLocalDate(), key -> new int[11]);
            bucket[0]++;
            if (!window.above) {
                bucket[1]++;
                below++;
            }
        }
        int consecutive = 0;
        int singles = 0;
        int[] lengths = new int[8];
        int longest = 0;
        List<Btc15StreakRunDto> runDtos = new ArrayList<>();
        for (int[] run : runs) {
            int length = run[0];
            longest = Math.max(longest, length);
            LocalDate day = windows.get(run[1]).start.atZone(CENTRAL).toLocalDate();
            int[] bucket = days.computeIfAbsent(day, key -> new int[11]);
            if (length == 1) {
                singles++;
                bucket[3]++;
            } else {
                consecutive++;
                bucket[2]++;
                int slot = Math.min(length, 7);
                lengths[slot]++;
                bucket[slot + 2]++;
            }
            if (length > 1) {
                SettledWindow first = windows.get(run[1]);
                SettledWindow last = windows.get(run[2]);
                runDtos.add(new Btc15StreakRunDto(
                        day,
                        DAY_LABEL.format(day),
                        length,
                        length - 1,
                        CLOCK.format(first.start.atZone(CENTRAL)),
                        CLOCK_MERIDIEM.format(last.end.atZone(CENTRAL))));
            }
        }
        for (int i = 0; i + 1 < windows.size(); i++) {
            if (!windows.get(i).above && !windows.get(i + 1).above) {
                days.computeIfAbsent(windows.get(i).start.atZone(CENTRAL).toLocalDate(), key -> new int[11])[10]++;
            }
        }
        List<Btc15StreakDayDto> dayDtos = new ArrayList<>();
        for (Map.Entry<LocalDate, int[]> entry : days.entrySet()) {
            int[] bucket = entry.getValue();
            dayDtos.add(new Btc15StreakDayDto(
                    entry.getKey(),
                    DAY_LABEL.format(entry.getKey()),
                    bucket[0],
                    bucket[1],
                    bucket[10],
                    bucket[2],
                    bucket[3],
                    bucket[4],
                    bucket[5],
                    bucket[6],
                    bucket[7],
                    bucket[8],
                    bucket[9]));
        }
        int windowsCount = windows.size();
        PatternRead pattern = pattern(windowsCount, below, noWithNext, noThenNo, longest);
        return new Btc15StreakDeskDto(
                series,
                seriesTitle(series),
                start,
                end,
                windowsCount,
                below,
                windowsCount - below,
                noThenNo,
                consecutive,
                singles,
                lengths[2],
                lengths[3],
                lengths[4],
                lengths[5],
                lengths[6],
                lengths[7],
                longest,
                rate(below, windowsCount),
                rate(noThenNo, noWithNext),
                pattern.pattern,
                pattern.headline,
                pattern.notes,
                dayDtos,
                runDtos);
    }

    private List<SettledWindow> settledWindows(String series, LocalDate start, LocalDate end) {
        List<JsonNode> markets = fetchMarkets(
                series,
                start.atStartOfDay(CENTRAL).toInstant(),
                end.plusDays(1).atStartOfDay(CENTRAL).toInstant(),
                20,
                true);
        List<SettledWindow> windows = new ArrayList<>();
        for (JsonNode market : markets) {
            Instant open = instant(market.path("open_time").asText(""));
            Instant close = instant(market.path("close_time").asText(""));
            Boolean above = aboveTarget(market.path("result").asText(""), decimal(market.get("expiration_value")), decimal(market.get("floor_strike")));
            if (open == null || close == null || above == null) {
                continue;
            }
            windows.add(new SettledWindow(open, close, above));
        }
        windows.sort(Comparator.comparing(SettledWindow::start));
        return windows;
    }

    private static PatternRead pattern(int windows, int below, int noWithNext, int noThenNo, int longest) {
        if (windows == 0) {
            return new PatternRead("empty", "No settled windows in this range", List.of(
                    "Pick a date that already has a settlement, or widen the range."));
        }
        double miss = below / (double) windows;
        List<String> notes = new ArrayList<>();
        notes.add(String.format(
                Locale.US,
                "%d of %d windows closed below the target, %.0f%%.",
                below,
                windows,
                miss * 100));
        String pattern = "coin-flip";
        String headline = "Misses look like a coin flip";
        if (noWithNext > 0) {
            double follow = noThenNo / (double) noWithNext;
            double gap = follow - miss;
            if (gap >= 0.06) {
                pattern = "bunched";
                headline = "Misses bunch together";
                notes.add(String.format(
                        Locale.US,
                        "After a miss, the next window also missed %.0f%% of the time, %.0f points above the overall miss rate.",
                        follow * 100,
                        gap * 100));
            } else if (gap <= -0.06) {
                pattern = "reverses";
                headline = "A miss usually flips the next window";
                notes.add(String.format(
                        Locale.US,
                        "After a miss, the next window also missed %.0f%% of the time, %.0f points below the overall miss rate.",
                        follow * 100,
                        Math.abs(gap) * 100));
            } else {
                notes.add(String.format(
                        Locale.US,
                        "After a miss, the next window also missed %.0f%% of the time. That sits next to the overall miss rate.",
                        follow * 100));
            }
        }
        double expected = Math.log(Math.max(windows, 2)) / Math.log(1.0 / Math.max(miss, 0.05));
        if (longest > expected + 4) {
            notes.add(String.format(
                    Locale.US,
                    "The longest run was %d windows. A coin flip over %d windows usually tops out nearer %.0f.",
                    longest,
                    windows,
                    expected));
        } else if (longest > 0) {
            notes.add(String.format(
                    Locale.US,
                    "The longest run was %d windows. Over %d windows, a run of that length is ordinary.",
                    longest,
                    windows));
        }
        return new PatternRead(pattern, headline, notes);
    }

    private static String seriesOrDefault(String raw) {
        if (raw == null || raw.isBlank()) {
            return "KXBTC15M";
        }
        String series = raw.trim().toUpperCase(Locale.ROOT);
        if (!series.matches("[A-Z0-9]{2,32}")) {
            throw new IllegalArgumentException("Series ticker must be letters and digits.");
        }
        return series;
    }

    private static String seriesTitle(String series) {
        return switch (series) {
            case "KXBTC15M" -> "BTC 15 min";
            case "KXETH15M" -> "ETH 15 min";
            default -> series;
        };
    }

    private List<JsonNode> fetchMarkets(Instant from, Instant to) {
        return fetchMarkets("KXBTC15M", from, to, 8, false);
    }

    private List<JsonNode> fetchMarkets(String series, Instant from, Instant to, int maxPages, boolean settledOnly) {
        List<JsonNode> matched = new ArrayList<>();
        String cursor = "";
        for (int page = 0; page < maxPages; page++) {
            String url = "https://api.elections.kalshi.com/trade-api/v2/markets?limit=200&series_ticker="
                    + URLEncoder.encode(series, StandardCharsets.UTF_8);
            if (settledOnly) {
                url += "&status=settled";
            }
            if (!cursor.isBlank()) {
                url += "&cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8);
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

    private record SettledWindow(Instant start, Instant end, boolean above) {}

    private record PatternRead(String pattern, String headline, List<String> notes) {}
}
