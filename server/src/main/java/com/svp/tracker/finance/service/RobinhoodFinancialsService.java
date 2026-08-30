package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.finance.domain.RobinhoodAgenticConnection;
import com.svp.tracker.finance.repository.RobinhoodAgenticConnectionRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Robinhood Agentic {@code get_financials}: quarterly revenue, net income, and margin. Requires the
 * current user's Agentic connection and the trading sidecar.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodFinancialsService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(45);
    private static final int DEFAULT_LIMIT = 12;

    private final RobinhoodAgenticProperties agenticProps;
    private final RobinhoodAgenticConnectionRepository connectionRepository;
    private final RobinhoodAgenticTokenService tokenService;
    private final RobinhoodMcpFinancialsClient mcpFinancialsClient;
    private final CurrentUserService currentUser;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public record FinancialsRow(
            int year,
            int quarter,
            String periodEndDate,
            Double revenue,
            Double grossProfit,
            Double netIncome,
            Double netMarginPct) {}

    public List<FinancialsRow> quarterlyFinancials(String symbolRaw) {
        String symbol = symbolRaw == null ? "" : symbolRaw.trim().toUpperCase(Locale.ROOT);
        if (symbol.isBlank()) {
            return List.of();
        }
        CacheEntry cached = cache.get(symbol);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.atMs < CACHE_TTL.toMillis()) {
            return cached.rows;
        }
        List<FinancialsRow> rows = fetch(symbol);
        if (!rows.isEmpty()) {
            cache.put(symbol, new CacheEntry(now, rows));
        }
        return rows;
    }

    private List<FinancialsRow> fetch(String symbol) {
        try {
            long owner = currentUser.requireUserId();
            RobinhoodAgenticConnection conn = connectionRepository.findByOwnerUserId(owner).orElse(null);
            if (conn == null) {
                log.warn("Robinhood get_financials skipped for {}: no Agentic connection", symbol);
                return List.of();
            }
            if (agenticProps.serviceConfigured()) {
                List<FinancialsRow> fromSidecar = fromSidecar(conn, symbol);
                if (!fromSidecar.isEmpty()) {
                    return fromSidecar;
                }
            }
            return tokenService.withFreshToken(
                    conn, token -> mcpFinancialsClient.quarterlyFinancials(token, symbol, DEFAULT_LIMIT));
        } catch (Exception e) {
            log.warn("Robinhood get_financials failed for {}: {}", symbol, e.toString());
            return List.of();
        }
    }

    private List<FinancialsRow> fromSidecar(RobinhoodAgenticConnection conn, String symbol) {
        try {
            JsonNode root = tokenService.fetchFinancials(conn, symbol, DEFAULT_LIMIT);
            JsonNode list = root.path("financials");
            if (!list.isArray() || list.isEmpty()) {
                return List.of();
            }
            List<FinancialsRow> out = new ArrayList<>();
            for (JsonNode n : list) {
                String periodEnd = text(n, "period_end_date");
                if (periodEnd.isBlank()) {
                    continue;
                }
                out.add(new FinancialsRow(
                        n.path("fiscal_year").asInt(0),
                        n.path("fiscal_quarter").asInt(0),
                        periodEnd,
                        parseNum(text(n, "revenue")),
                        parseNum(text(n, "gross_profit")),
                        parseNum(text(n, "net_income")),
                        parseNum(text(n, "net_margin"))));
            }
            return out;
        } catch (Exception e) {
            log.warn("Sidecar get_financials empty for {}: {}", symbol, e.toString());
            return List.of();
        }
    }

    private static String text(JsonNode n, String field) {
        if (n == null || !n.isObject()) {
            return "";
        }
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? "" : v.asText("").trim();
    }

    private static Double parseNum(String raw) {
        if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) {
            return null;
        }
        try {
            double v = Double.parseDouble(raw.trim());
            return Double.isFinite(v) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record CacheEntry(long atMs, List<FinancialsRow> rows) {}
}
