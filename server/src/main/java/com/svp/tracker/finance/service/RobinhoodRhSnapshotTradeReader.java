package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.finance.dto.RobinhoodRhDailyTradeDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/** Reads frozen Daily Tracker {@code trades_json}, including numeric epoch {@code executedAt}. */
@Slf4j
final class RobinhoodRhSnapshotTradeReader {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private RobinhoodRhSnapshotTradeReader() {}

    static List<RobinhoodRhDailyTradeDto> read(String tradesJson) {
        if (tradesJson == null || tradesJson.isBlank() || "[]".equals(tradesJson.trim())) {
            return List.of();
        }
        try {
            JsonNode root = MAPPER.readTree(tradesJson);
            if (root == null || !root.isArray()) {
                return List.of();
            }
            List<RobinhoodRhDailyTradeDto> out = new ArrayList<>();
            for (JsonNode n : root) {
                if (n == null || !n.isObject()) {
                    continue;
                }
                String symbol = text(n, "symbol");
                if (symbol == null || symbol.isBlank()) {
                    continue;
                }
                out.add(new RobinhoodRhDailyTradeDto(
                        symbol,
                        text(n, "side"),
                        text(n, "orderType"),
                        decimal(n, "quantity"),
                        decimal(n, "averagePrice"),
                        decimal(n, "limitPrice"),
                        text(n, "state"),
                        instant(n.get("executedAt")),
                        text(n, "accountSuffix"),
                        text(n, "accountLabel")));
            }
            return out;
        } catch (Exception e) {
            log.debug("Could not parse trades_json: {}", e.getMessage());
            return List.of();
        }
    }

    static Instant instant(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) {
            return null;
        }
        if (n.isNumber()) {
            double v = n.asDouble();
            if (v > 1.0e12) {
                return Instant.ofEpochMilli(Math.round(v));
            }
            long seconds = (long) v;
            long nanos = Math.round((v - seconds) * 1_000_000_000L);
            return Instant.ofEpochSecond(seconds, nanos);
        }
        if (n.isTextual()) {
            String raw = n.asText();
            if (raw == null || raw.isBlank()) {
                return null;
            }
            try {
                return Instant.parse(raw);
            } catch (Exception ignored) {
                try {
                    return instant(MAPPER.readTree(raw));
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull() || !v.isValueNode()) {
            return null;
        }
        String s = v.asText();
        return s == null || s.isBlank() ? null : s;
    }

    private static BigDecimal decimal(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull() || v.isMissingNode()) {
            return null;
        }
        if (v.isNumber()) {
            return v.decimalValue();
        }
        if (v.isTextual()) {
            try {
                return new BigDecimal(v.asText().trim());
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
