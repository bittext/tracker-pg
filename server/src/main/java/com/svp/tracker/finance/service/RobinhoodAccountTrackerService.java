package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.finance.domain.RobinhoodAccountTrackerConfig;
import com.svp.tracker.finance.domain.RobinhoodAgenticPosition;
import com.svp.tracker.finance.dto.RobinhoodAccountLedgerEventDto;
import com.svp.tracker.finance.dto.RobinhoodAccountTrackerDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticSpxComparisonDto;
import com.svp.tracker.finance.dto.RobinhoodIndividualNbisTrackerDto;
import com.svp.tracker.finance.repository.RobinhoodAccountTrackerConfigRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticPositionRepository;
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
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodAccountTrackerService {

    private static final ZoneId CENTRAL = ZoneId.of("America/Chicago");
    private static final String SPX_SYMBOL = "^GSPC";
    private static final String NBIS = "NBIS";
    private static final BigDecimal DEFAULT_BASELINE_NBIS = new BigDecimal("732");
    private static final String YAHOO_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String CHART_3MO =
            "https://query1.finance.yahoo.com/v8/finance/chart/%s"
                    + "?range=3mo&interval=1d&includeAdjustedClose=true";

    private final RobinhoodAccountTrackerConfigRepository configRepository;
    private final RobinhoodAgenticPositionRepository positionRepository;
    private final RobinhoodFinanceService financeService;
    private final FinanceProperties financeProperties;
    private final CurrentUserService currentUser;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .build();

    @Transactional
    public RobinhoodAccountTrackerDto buildTracker() {
        long ownerUserId = currentUser.requireUserId();
        RobinhoodAccountTrackerConfig config = getOrCreateConfig(ownerUserId);
        Instant trackingStartedAt = config.getTrackingStartedAt();
        String individualSuffix = config.getIndividualAccountSuffix();
        String agenticSuffix = config.getAgenticAccountSuffix();

        int ledgerCap = Math.max(financeProperties.maxStocksSummaryRows(), 500);
        List<Map<String, Object>> rows = financeService.fetchTransactionMapsSince(trackingStartedAt, null, ledgerCap);
        List<RobinhoodAccountLedgerEventDto> ledger = rows.stream().map(this::toLedgerEvent).toList();

        BigDecimal bought = BigDecimal.ZERO;
        BigDecimal sold = BigDecimal.ZERO;
        for (RobinhoodAccountLedgerEventDto event : ledger) {
            if (!isNbisInstrument(event.instrument())) {
                continue;
            }
            if ("IN".equals(event.direction()) && isTradeCategory(event.category())) {
                bought = bought.add(absQty(event.quantity()));
            } else if ("OUT".equals(event.direction()) && isTradeCategory(event.category())) {
                sold = sold.add(absQty(event.quantity()));
            }
        }

        List<RobinhoodAgenticPosition> allPositions = positionRepository.findByOwnerUserIdOrderBySymbolAsc(ownerUserId);
        NbisLive liveNbis = liveNbisForSuffix(allPositions, individualSuffix);

        BigDecimal baseline = config.getIndividualBaselineNbis();
        BigDecimal expected = baseline.add(bought).subtract(sold);
        BigDecimal variance = liveNbis.quantity.subtract(expected);

        RobinhoodIndividualNbisTrackerDto individual = new RobinhoodIndividualNbisTrackerDto(
                maskSuffix(individualSuffix),
                trackingStartedAt,
                baseline,
                bought,
                sold,
                expected,
                liveNbis.quantity,
                variance,
                liveNbis.syncedAt,
                liveNbis.fromSync);

        AgenticTotals agentic = agenticTotalsForSuffix(allPositions, agenticSuffix);
        SpxReturn spx = fetchSpxReturn(trackingStartedAt);
        BigDecimal agenticReturnPct = agenticReturnPct(agentic, config.getAgenticBaselineMarketValue());
        String agenticBasis = agenticReturnBasis(config.getAgenticBaselineMarketValue(), agentic);

        RobinhoodAgenticSpxComparisonDto comparison = new RobinhoodAgenticSpxComparisonDto(
                maskSuffix(agenticSuffix),
                trackingStartedAt,
                SPX_SYMBOL,
                spx.startDate,
                spx.startPrice,
                spx.currentPrice,
                spx.returnPct,
                agentic.marketValue,
                agentic.costBasis,
                config.getAgenticBaselineMarketValue(),
                agenticReturnPct,
                agenticBasis,
                agentic.syncedAt);

        List<String> notes = buildNotes(ledger.size(), ledgerCap, liveNbis, variance);

        return new RobinhoodAccountTrackerDto(
                trackingStartedAt,
                individualSuffix,
                agenticSuffix,
                individual,
                comparison,
                ledger,
                notes);
    }

    private RobinhoodAccountTrackerConfig getOrCreateConfig(long ownerUserId) {
        return configRepository
                .findByOwnerUserId(ownerUserId)
                .orElseGet(() -> createDefaultConfig(ownerUserId));
    }

    private RobinhoodAccountTrackerConfig createDefaultConfig(long ownerUserId) {
        Instant now = Instant.now();
        Instant trackingStart = ZonedDateTime.of(2026, 6, 24, 0, 0, 0, 0, CENTRAL).toInstant();

        RobinhoodAccountTrackerConfig config = new RobinhoodAccountTrackerConfig();
        config.setOwnerUserId(ownerUserId);
        config.setTrackingStartedAt(trackingStart);
        config.setIndividualAccountSuffix("3370");
        config.setIndividualBaselineNbis(DEFAULT_BASELINE_NBIS);
        config.setAgenticAccountSuffix("3550");
        config.setCreatedAt(now);
        config.setUpdatedAt(now);
        return configRepository.save(config);
    }

    private static List<String> buildNotes(int ledgerSize, int ledgerCap, NbisLive liveNbis, BigDecimal variance) {
        List<String> notes = new ArrayList<>();
        if (ledgerSize >= ledgerCap) {
            notes.add("Ledger capped at " + ledgerCap + " rows; older activity since cutoff may be omitted.");
        }
        if (!liveNbis.fromSync) {
            notes.add("Live NBIS not found in synced positions for individual account — sync Agentic connection.");
        }
        if (variance != null && variance.abs().compareTo(new BigDecimal("0.000001")) > 0) {
            notes.add("NBIS variance vs baseline + CSV trades since cutoff — check pre-cutoff activity or transfers.");
        }
        return notes;
    }

    private RobinhoodAccountLedgerEventDto toLedgerEvent(Map<String, Object> row) {
        String transCode = norm(stringCell(row, "TRANS_CODE"));
        if (transCode.isBlank()) {
            transCode = norm(stringCell(row, "trans_code"));
        }
        String direction = ledgerDirection(transCode);
        String category = ledgerCategory(transCode);
        return new RobinhoodAccountLedgerEventDto(
                localDateCell(row, "ACTIVITY_DATE", "activity_date"),
                trimOrNull(stringCell(row, "INSTRUMENT", "instrument")),
                trimOrNull(stringCell(row, "DESCRIPTION", "description")),
                transCode,
                decimalCell(row, "QUANTITY", "quantity"),
                decimalCell(row, "PRICE", "price"),
                decimalCell(row, "AMOUNT", "amount"),
                direction,
                category);
    }

    private static String ledgerDirection(String transCode) {
        return switch (transCode) {
            case "BTO", "BTC", "BUY" -> "IN";
            case "STC", "STO", "SELL" -> "OUT";
            default -> "OTHER";
        };
    }

    private static String ledgerCategory(String transCode) {
        if (transCode.isBlank()) {
            return "OTHER";
        }
        return switch (transCode) {
            case "BTO", "STC", "STO", "BTC", "BUY", "SELL" -> "TRADE";
            case "DIV", "DIVIDEND" -> "DIVIDEND";
            case "ACH", "XENT", "INT", "MINT" -> "TRANSFER";
            default -> transCode.startsWith("ACH") ? "TRANSFER" : "OTHER";
        };
    }

    private static boolean isTradeCategory(String category) {
        return "TRADE".equals(category);
    }

    private static boolean isNbisInstrument(String instrument) {
        if (instrument == null || instrument.isBlank()) {
            return false;
        }
        String u = instrument.toUpperCase(Locale.ROOT);
        return u.equals(NBIS) || u.endsWith("/" + NBIS + "/") || u.contains("/" + NBIS);
    }

    private static BigDecimal absQty(BigDecimal qty) {
        return qty == null ? BigDecimal.ZERO : qty.abs();
    }

    private static NbisLive liveNbisForSuffix(List<RobinhoodAgenticPosition> positions, String suffix) {
        BigDecimal total = BigDecimal.ZERO;
        Instant syncedAt = null;
        boolean found = false;
        for (RobinhoodAgenticPosition p : positions) {
            if (!accountEndsWith(p.getAccountNumber(), suffix)) {
                continue;
            }
            if (!NBIS.equalsIgnoreCase(Objects.toString(p.getSymbol(), ""))) {
                continue;
            }
            if (!"equity".equalsIgnoreCase(Objects.toString(p.getPositionType(), "equity"))) {
                continue;
            }
            found = true;
            total = total.add(nullToZero(p.getQuantity()));
            if (p.getSyncedAt() != null && (syncedAt == null || p.getSyncedAt().isAfter(syncedAt))) {
                syncedAt = p.getSyncedAt();
            }
        }
        return new NbisLive(total, syncedAt, found);
    }

    private static AgenticTotals agenticTotalsForSuffix(List<RobinhoodAgenticPosition> positions, String suffix) {
        BigDecimal marketValue = BigDecimal.ZERO;
        BigDecimal costBasis = BigDecimal.ZERO;
        Instant syncedAt = null;
        for (RobinhoodAgenticPosition p : positions) {
            if (!accountEndsWith(p.getAccountNumber(), suffix)) {
                continue;
            }
            BigDecimal qty = nullToZero(p.getQuantity());
            if (qty.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            marketValue = marketValue.add(nullToZero(p.getMarketValue()));
            BigDecimal avg = nullToZero(p.getAverageBuyPrice());
            costBasis = costBasis.add(qty.abs().multiply(avg));
            if (p.getSyncedAt() != null && (syncedAt == null || p.getSyncedAt().isAfter(syncedAt))) {
                syncedAt = p.getSyncedAt();
            }
        }
        return new AgenticTotals(marketValue, costBasis, syncedAt);
    }

    private static boolean accountEndsWith(String accountNumber, String suffix) {
        if (accountNumber == null || suffix == null || suffix.isBlank()) {
            return false;
        }
        String trimmed = accountNumber.trim();
        return trimmed.endsWith(suffix);
    }

    private static String maskSuffix(String suffix) {
        return "••••" + (suffix == null ? "" : suffix);
    }

    private static BigDecimal agenticReturnPct(AgenticTotals agentic, BigDecimal baselineMv) {
        BigDecimal base = baselineMv != null && baselineMv.compareTo(BigDecimal.ZERO) > 0
                ? baselineMv
                : agentic.costBasis;
        if (base == null || base.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return agentic.marketValue
                .subtract(base)
                .divide(base, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }

    private static String agenticReturnBasis(BigDecimal baselineMv, AgenticTotals agentic) {
        if (baselineMv != null && baselineMv.compareTo(BigDecimal.ZERO) > 0) {
            return "vs baseline market value at tracking start";
        }
        if (agentic.costBasis.compareTo(BigDecimal.ZERO) > 0) {
            return "vs synced cost basis (no baseline MV stored)";
        }
        return "n/a";
    }

    private SpxReturn fetchSpxReturn(Instant trackingStartedAt) {
        try {
            String url = String.format(CHART_3MO, encSymbol(SPX_SYMBOL));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", YAHOO_USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Yahoo SPX chart HTTP {}", response.statusCode());
                return SpxReturn.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode chart = root.path("chart").path("result");
            if (!chart.isArray() || chart.isEmpty()) {
                return SpxReturn.empty();
            }
            JsonNode result = chart.get(0);
            long[] times = parseTimes(result.path("timestamp"));
            double[] closes = extractAdjCloseSeries(result);
            if (times.length == 0 || closes.length != times.length) {
                return SpxReturn.empty();
            }
            LocalDate cutoff = trackingStartedAt.atZone(CENTRAL).toLocalDate();
            int startIdx = -1;
            for (int i = 0; i < times.length; i++) {
                if (times[i] <= 0 || closes[i] <= 0 || Double.isNaN(closes[i])) {
                    continue;
                }
                LocalDate d = Instant.ofEpochSecond(times[i]).atZone(ZoneId.of("America/New_York")).toLocalDate();
                if (!d.isAfter(cutoff)) {
                    startIdx = i;
                }
            }
            int lastIdx = closes.length - 1;
            while (lastIdx > 0 && (closes[lastIdx] <= 0 || Double.isNaN(closes[lastIdx]))) {
                lastIdx--;
            }
            if (startIdx < 0 || lastIdx < 0) {
                return SpxReturn.empty();
            }
            double startPx = closes[startIdx];
            double endPx = closes[lastIdx];
            if (startPx <= 0) {
                return SpxReturn.empty();
            }
            LocalDate startDate =
                    Instant.ofEpochSecond(times[startIdx]).atZone(ZoneId.of("America/New_York")).toLocalDate();
            BigDecimal returnPct = BigDecimal.valueOf(100.0 * (endPx / startPx - 1.0)).setScale(2, RoundingMode.HALF_UP);
            return new SpxReturn(
                    startDate,
                    bd(startPx),
                    bd(endPx),
                    returnPct);
        } catch (Exception e) {
            log.warn("SPX return fetch failed: {}", e.getMessage());
            return SpxReturn.empty();
        }
    }

    private static long[] parseTimes(JsonNode timesNode) {
        if (!timesNode.isArray()) {
            return new long[0];
        }
        long[] out = new long[timesNode.size()];
        for (int i = 0; i < timesNode.size(); i++) {
            out[i] = timesNode.get(i).asLong(0L);
        }
        return out;
    }

    private static double[] extractAdjCloseSeries(JsonNode resultNode) {
        JsonNode priceSeries = null;
        JsonNode adj = resultNode.path("indicators").path("adjclose");
        if (adj.isArray() && !adj.isEmpty() && adj.get(0) != null) {
            priceSeries = adj.get(0).get("adjclose");
        }
        if (priceSeries == null || !priceSeries.isArray()) {
            JsonNode quote = resultNode.path("indicators").path("quote");
            if (quote.isArray() && !quote.isEmpty() && quote.get(0) != null) {
                priceSeries = quote.get(0).get("close");
            }
        }
        if (priceSeries == null || !priceSeries.isArray()) {
            return new double[0];
        }
        int n = priceSeries.size();
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            JsonNode px = priceSeries.get(i);
            out[i] = px != null && px.isNumber() ? px.asDouble() : Double.NaN;
        }
        return out;
    }

    private static String encSymbol(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nullToZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    private static String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String stringCell(Map<String, Object> row, String... names) {
        for (String name : names) {
            Object v = rawCell(row, name);
            if (v != null) {
                return v.toString();
            }
        }
        return null;
    }

    private static BigDecimal decimalCell(Map<String, Object> row, String... names) {
        for (String name : names) {
            Object v = rawCell(row, name);
            if (v == null) {
                continue;
            }
            if (v instanceof BigDecimal bd) {
                return bd;
            }
            if (v instanceof Number n) {
                return BigDecimal.valueOf(n.doubleValue());
            }
            try {
                return new BigDecimal(v.toString().trim());
            } catch (NumberFormatException ignored) {
                // try next name
            }
        }
        return null;
    }

    private static LocalDate localDateCell(Map<String, Object> row, String... names) {
        for (String name : names) {
            Object v = rawCell(row, name);
            if (v == null) {
                continue;
            }
            if (v instanceof LocalDate ld) {
                return ld;
            }
            if (v instanceof java.sql.Timestamp ts) {
                return ts.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
            if (v instanceof java.util.Date d) {
                return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
            if (v instanceof java.sql.Date sd) {
                return sd.toLocalDate();
            }
            if (v instanceof String s && s.trim().length() >= 10) {
                return LocalDate.parse(s.trim().substring(0, 10));
            }
        }
        return null;
    }

    private static Object rawCell(Map<String, Object> row, String name) {
        Object v = row.get(name);
        if (v != null) {
            return v;
        }
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    private record NbisLive(BigDecimal quantity, Instant syncedAt, boolean fromSync) {}

    private record AgenticTotals(BigDecimal marketValue, BigDecimal costBasis, Instant syncedAt) {}

    private record SpxReturn(LocalDate startDate, BigDecimal startPrice, BigDecimal currentPrice, BigDecimal returnPct) {
        static SpxReturn empty() {
            return new SpxReturn(null, null, null, null);
        }
    }
}
