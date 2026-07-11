package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.svp.tracker.finance.dto.RhDailyTrackerAiFactsDigestDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailyTradeDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailyTrackerDayDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Builds a bounded JSON facts bundle from Daily Tracker days for LLM coaching.
 * Does not invent realized P&amp;L — only activity, flows, and account-value trajectory.
 */
public final class RhDailyTrackerAiFactsBuilder {

    private static final int MAX_SAMPLE_TRADES = 40;
    private static final int MAX_NOTES = 12;
    private static final int NOTE_MAX_CHARS = 240;

    private RhDailyTrackerAiFactsBuilder() {}

    public record FactsBundle(String factsJson, String factsHash, RhDailyTrackerAiFactsDigestDto digest) {}

    public static FactsBundle build(
            ObjectMapper mapper,
            String scope,
            String periodKey,
            String periodLabel,
            List<RobinhoodRhDailyTrackerDayDto> daysInRange)
            throws Exception {
        List<RobinhoodRhDailyTrackerDayDto> days = daysInRange == null ? List.of() : daysInRange.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(RobinhoodRhDailyTrackerDayDto::snapshotDate))
                .toList();

        List<RobinhoodRhDailyTradeDto> allTrades = new ArrayList<>();
        for (RobinhoodRhDailyTrackerDayDto day : days) {
            if (day.trades() != null) {
                allTrades.addAll(day.trades());
            }
        }

        int buyCount = 0;
        int sellCount = 0;
        Map<String, Integer> countBySymbol = new HashMap<>();
        Map<String, BigDecimal> notionalBySymbol = new HashMap<>();
        Map<String, Integer> orderTypeCounts = new HashMap<>();
        Map<DayOfWeek, Integer> weekdayCounts = new HashMap<>();
        List<BigDecimal> notionals = new ArrayList<>();
        Map<LocalDate, Integer> tradesByDay = new HashMap<>();

        for (RobinhoodRhDailyTradeDto t : allTrades) {
            String side = normalizeSide(t.side());
            if ("buy".equals(side)) {
                buyCount++;
            } else if ("sell".equals(side)) {
                sellCount++;
            }
            String symbol = t.symbol() == null || t.symbol().isBlank() ? "UNKNOWN" : t.symbol().trim().toUpperCase(Locale.ROOT);
            countBySymbol.merge(symbol, 1, Integer::sum);
            BigDecimal notional = tradeNotional(t);
            if (notional != null) {
                notionalBySymbol.merge(symbol, notional, BigDecimal::add);
                notionals.add(notional);
            }
            String ot = t.orderType() == null || t.orderType().isBlank() ? "unknown" : t.orderType().trim().toLowerCase(Locale.ROOT);
            orderTypeCounts.merge(ot, 1, Integer::sum);
            if (t.executedAt() != null) {
                LocalDate d = t.executedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate();
                tradesByDay.merge(d, 1, Integer::sum);
                weekdayCounts.merge(d.getDayOfWeek(), 1, Integer::sum);
            }
        }

        List<String> topByCount = topKeys(countBySymbol, 8);
        List<String> topByNotional = topKeysBigDecimal(notionalBySymbol, 8);

        BigDecimal valueStart = null;
        BigDecimal valueEnd = null;
        BigDecimal periodAdded = BigDecimal.ZERO;
        BigDecimal periodRemoved = BigDecimal.ZERO;
        LocalDate largestUpDay = null;
        BigDecimal largestUp = null;
        LocalDate largestDownDay = null;
        BigDecimal largestDown = null;
        ArrayNode valueSeries = mapper.createArrayNode();

        for (RobinhoodRhDailyTrackerDayDto day : days) {
            if (!day.hasScheduledSnapshot()) {
                continue;
            }
            BigDecimal total = nz(day.combinedTotal());
            if (valueStart == null) {
                valueStart = total;
            }
            valueEnd = total;
            periodAdded = periodAdded.add(nz(day.combinedPeriodAdded()));
            periodRemoved = periodRemoved.add(nz(day.combinedPeriodRemoved()));
            BigDecimal chg = nz(day.combinedTotalChangeFromPrevious());
            if (day.hasPreviousScheduledSnapshot()) {
                if (largestUp == null || chg.compareTo(largestUp) > 0) {
                    largestUp = chg;
                    largestUpDay = day.snapshotDate();
                }
                if (largestDown == null || chg.compareTo(largestDown) < 0) {
                    largestDown = chg;
                    largestDownDay = day.snapshotDate();
                }
            }
            ObjectNode pt = mapper.createObjectNode();
            pt.put("date", day.snapshotDate().toString());
            pt.put("combinedTotal", total);
            pt.put("changeFromPrevious", chg);
            pt.put("tradeCount", day.trades() == null ? 0 : day.trades().size());
            valueSeries.add(pt);
        }

        BigDecimal valueChange =
                valueStart != null && valueEnd != null ? valueEnd.subtract(valueStart) : null;

        List<ObjectNode> sampleTrades = sampleTrades(mapper, allTrades);
        ArrayNode notes = mapper.createArrayNode();
        int noteCount = 0;
        for (int i = days.size() - 1; i >= 0 && noteCount < MAX_NOTES; i--) {
            RobinhoodRhDailyTrackerDayDto day = days.get(i);
            String note = day.summaryNote();
            if (note == null || note.isBlank()) {
                continue;
            }
            ObjectNode n = mapper.createObjectNode();
            n.put("date", day.snapshotDate().toString());
            String trimmed = note.trim();
            if (trimmed.length() > NOTE_MAX_CHARS) {
                trimmed = trimmed.substring(0, NOTE_MAX_CHARS) + "…";
            }
            n.put("note", trimmed);
            notes.add(n);
            noteCount++;
        }

        String busiestWeekday = weekdayCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> e.getKey().name())
                .orElse(null);

        BigDecimal avgNotional = notionals.isEmpty()
                ? null
                : notionals.stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(notionals.size()), 2, RoundingMode.HALF_UP);
        BigDecimal medianNotional = median(notionals);

        ObjectNode root = mapper.createObjectNode();
        root.put("disclaimer", "Facts describe Daily Tracker activity and account-value movement only. There is no realized P&L or win rate in this data.");
        root.put("scope", scope);
        root.put("periodKey", periodKey);
        root.put("periodLabel", periodLabel);
        root.put("dayCount", days.size());
        root.put("scheduledSnapshotDays", (int) days.stream().filter(RobinhoodRhDailyTrackerDayDto::hasScheduledSnapshot).count());
        root.put("tradeCount", allTrades.size());
        root.put("buyCount", buyCount);
        root.put("sellCount", sellCount);
        root.put("uniqueSymbols", countBySymbol.size());
        root.put("activeTradeDays", tradesByDay.size());
        root.put("tradesPerActiveDay", tradesByDay.isEmpty()
                ? 0.0
                : (double) allTrades.size() / tradesByDay.size());
        if (busiestWeekday != null) {
            root.put("busiestWeekday", busiestWeekday);
        }
        if (avgNotional != null) {
            root.put("avgTradeNotional", avgNotional);
        }
        if (medianNotional != null) {
            root.put("medianTradeNotional", medianNotional);
        }
        if (valueStart != null) {
            root.put("accountValueStart", valueStart);
        }
        if (valueEnd != null) {
            root.put("accountValueEnd", valueEnd);
        }
        if (valueChange != null) {
            root.put("accountValueChange", valueChange);
        }
        root.put("periodAdded", periodAdded);
        root.put("periodRemoved", periodRemoved);
        if (largestUpDay != null) {
            root.put("largestUpDay", largestUpDay.toString());
            root.put("largestUpChange", largestUp);
        }
        if (largestDownDay != null) {
            root.put("largestDownDay", largestDownDay.toString());
            root.put("largestDownChange", largestDown);
        }

        ArrayNode topCountArr = root.putArray("topSymbolsByCount");
        for (String s : topByCount) {
            ObjectNode row = topCountArr.addObject();
            row.put("symbol", s);
            row.put("count", countBySymbol.getOrDefault(s, 0));
        }
        ArrayNode topNotionalArr = root.putArray("topSymbolsByNotional");
        for (String s : topByNotional) {
            ObjectNode row = topNotionalArr.addObject();
            row.put("symbol", s);
            row.put("notional", notionalBySymbol.getOrDefault(s, BigDecimal.ZERO));
        }
        ObjectNode orderTypes = root.putObject("orderTypeCounts");
        for (Map.Entry<String, Integer> e : orderTypeCounts.entrySet()) {
            orderTypes.put(e.getKey(), e.getValue());
        }
        root.set("accountValueSeries", valueSeries);
        ArrayNode sampleArr = root.putArray("sampleTrades");
        for (ObjectNode t : sampleTrades) {
            sampleArr.add(t);
        }
        if (allTrades.size() > sampleTrades.size()) {
            root.put("sampleTradesOmitted", allTrades.size() - sampleTrades.size());
        }
        root.set("recentDayNotes", notes);

        String factsJson = mapper.writeValueAsString(root);
        String hash = sha256Hex(factsJson);
        RhDailyTrackerAiFactsDigestDto digest = new RhDailyTrackerAiFactsDigestDto(
                allTrades.size(),
                buyCount,
                sellCount,
                countBySymbol.size(),
                tradesByDay.size(),
                valueStart,
                valueEnd,
                valueChange,
                periodAdded,
                periodRemoved,
                topByCount,
                topByNotional);
        return new FactsBundle(factsJson, hash, digest);
    }

    private static List<ObjectNode> sampleTrades(ObjectMapper mapper, List<RobinhoodRhDailyTradeDto> trades) {
        List<RobinhoodRhDailyTradeDto> ranked = new ArrayList<>(trades);
        ranked.sort((a, b) -> {
            BigDecimal na = tradeNotional(a);
            BigDecimal nb = tradeNotional(b);
            int cmp = nz(nb).compareTo(nz(na));
            if (cmp != 0) {
                return cmp;
            }
            InstantSafe ia = InstantSafe.of(a);
            InstantSafe ib = InstantSafe.of(b);
            return ib.compareTo(ia);
        });
        List<ObjectNode> out = new ArrayList<>();
        int n = Math.min(MAX_SAMPLE_TRADES, ranked.size());
        for (int i = 0; i < n; i++) {
            RobinhoodRhDailyTradeDto t = ranked.get(i);
            ObjectNode node = mapper.createObjectNode();
            node.put("symbol", t.symbol());
            if (t.side() != null) {
                node.put("side", t.side());
            }
            if (t.orderType() != null) {
                node.put("orderType", t.orderType());
            }
            if (t.quantity() != null) {
                node.put("quantity", t.quantity());
            }
            if (t.averagePrice() != null) {
                node.put("averagePrice", t.averagePrice());
            }
            BigDecimal notional = tradeNotional(t);
            if (notional != null) {
                node.put("notional", notional);
            }
            if (t.executedAt() != null) {
                node.put("executedAt", t.executedAt().toString());
            }
            if (t.accountSuffix() != null) {
                node.put("accountSuffix", t.accountSuffix());
            }
            out.add(node);
        }
        return out;
    }

    private record InstantSafe(long epoch) implements Comparable<InstantSafe> {
        static InstantSafe of(RobinhoodRhDailyTradeDto t) {
            return new InstantSafe(t.executedAt() == null ? 0L : t.executedAt().toEpochMilli());
        }

        @Override
        public int compareTo(InstantSafe o) {
            return Long.compare(epoch, o.epoch);
        }
    }

    private static BigDecimal tradeNotional(RobinhoodRhDailyTradeDto t) {
        if (t.quantity() == null || t.averagePrice() == null) {
            return null;
        }
        return t.quantity().abs().multiply(t.averagePrice().abs()).setScale(2, RoundingMode.HALF_UP);
    }

    private static String normalizeSide(String side) {
        if (side == null) {
            return "";
        }
        String s = side.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("buy") || s.equals("b")) {
            return "buy";
        }
        if (s.startsWith("sell") || s.equals("s")) {
            return "sell";
        }
        return s;
    }

    private static List<String> topKeys(Map<String, Integer> map, int limit) {
        return map.entrySet().stream()
                .sorted((a, b) -> {
                    int c = Integer.compare(b.getValue(), a.getValue());
                    return c != 0 ? c : a.getKey().compareTo(b.getKey());
                })
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    private static List<String> topKeysBigDecimal(Map<String, BigDecimal> map, int limit) {
        return map.entrySet().stream()
                .sorted((a, b) -> {
                    int c = b.getValue().compareTo(a.getValue());
                    return c != 0 ? c : a.getKey().compareTo(b.getKey());
                })
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    private static BigDecimal median(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<BigDecimal> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(mid);
        }
        return sorted.get(mid - 1).add(sorted.get(mid)).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    static String sha256Hex(String text) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] dig = md.digest(text.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(dig);
    }
}
