package com.svp.tracker.finance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.finance.domain.RobinhoodAgenticConnection;
import com.svp.tracker.finance.domain.RobinhoodRhDailySnapshot;
import com.svp.tracker.finance.dto.FinanceTaxDeskRhAccountRealizedDto;
import com.svp.tracker.finance.dto.RobinhoodRhHoldingDto;
import com.svp.tracker.finance.dto.RobinhoodYtdCheckCloseDto;
import com.svp.tracker.finance.dto.RobinhoodYtdCheckDto;
import com.svp.tracker.finance.dto.RobinhoodYtdCheckPositionDto;
import com.svp.tracker.finance.repository.RobinhoodAgenticConnectionRepository;
import com.svp.tracker.finance.repository.RobinhoodRhDailySnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Individual ••••3370 broker YTD realized vs the open mark. Live sidecar when connected;
 * otherwise stored tax-desk realized plus the latest Daily Tracker snapshot.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodYtdCheckService {

    static final String SUFFIX = "3370";
    static final String LABEL = "Individual";
    private static final ZoneId EASTERN = ZoneId.of("America/New_York");
    private static final String NOTE =
            "YTD realized is closed trades only. The larger YTD line on the Robinhood app also marks open lots."
                    + " Implied YTD here is realized plus the current open mark, which is close to that app line."
                    + " Informational, not tax advice.";

    private final CurrentUserService currentUser;
    private final RobinhoodAgenticProperties agenticProps;
    private final RobinhoodAgenticConnectionRepository connectionRepository;
    private final RobinhoodAgenticTokenService tokenService;
    private final RobinhoodBrokerRealizedPnlService brokerRealized;
    private final RobinhoodRhDailySnapshotRepository snapshotRepository;
    /** Spring Boot 4 does not expose an ObjectMapper bean; local mapper for snapshot holdings JSON. */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RobinhoodYtdCheckDto load(int year) {
        long owner = currentUser.requireUserId();
        LocalDate asOf = LocalDate.now(EASTERN);
        if (year != asOf.getYear()) {
            asOf = LocalDate.of(year, 12, 31);
        }
        Optional<RobinhoodYtdCheckDto> live = fetchLive(owner, year, asOf);
        if (live.isPresent()) {
            return live.get();
        }
        return fallback(owner, year, asOf);
    }

    private Optional<RobinhoodYtdCheckDto> fetchLive(long owner, int year, LocalDate asOf) {
        if (!agenticProps.serviceConfigured()) {
            return Optional.empty();
        }
        RobinhoodAgenticConnection conn = connectionRepository.findByOwnerUserId(owner).orElse(null);
        if (conn == null) {
            return Optional.empty();
        }
        try {
            JsonNode root = tokenService.fetchYtdCheck(conn, year, asOf.toString());
            return Optional.of(fromSidecar(root, year, asOf, Instant.now()));
        } catch (Exception e) {
            log.warn("YTD check sidecar failed for user {}: {}", owner, e.toString());
            return Optional.empty();
        }
    }

    static RobinhoodYtdCheckDto fromSidecar(JsonNode root, int year, LocalDate asOf, Instant fetchedAt) {
        List<String> warnings = readWarnings(root);
        BigDecimal realizedYtd = decimal(root, "realized_ytd");
        BigDecimal equityValue = decimal(root, "equity_value");
        List<RobinhoodYtdCheckPositionDto> positions = readPositions(root.get("positions"), equityValue);
        BigDecimal openUnrealized = positions.stream()
                .map(RobinhoodYtdCheckPositionDto::unrealized)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal implied = realizedYtd == null ? null : realizedYtd.add(openUnrealized).setScale(2, RoundingMode.HALF_UP);
        LocalDate parsedAsOf = textDate(root, "as_of").orElse(asOf);
        return new RobinhoodYtdCheckDto(
                SUFFIX,
                LABEL,
                year,
                parsedAsOf,
                fetchedAt,
                true,
                "ROBINHOOD",
                decimal(root, "account_value"),
                equityValue,
                decimal(root, "cash"),
                decimal(root, "buying_power"),
                realizedYtd,
                decimal(root, "realized_equity"),
                decimal(root, "realized_option"),
                decimal(root, "realized_crypto"),
                decimal(root, "realized_calendar_day"),
                decimal(root, "realized_app_day"),
                root.path("app_day_trades").asInt(0),
                openUnrealized,
                implied,
                NOTE,
                positions,
                readCloses(root.get("recent_closes")),
                warnings);
    }

    private RobinhoodYtdCheckDto fallback(long owner, int year, LocalDate asOf) {
        List<String> warnings = new ArrayList<>();
        warnings.add("Live broker YTD check was unavailable. Showing stored realized P&L and the latest snapshot.");
        BigDecimal realizedYtd = null;
        Optional<RobinhoodBrokerRealizedPnlService.Fetched> stored =
                brokerRealized.storedOnOrBefore(owner, year, asOf);
        if (stored.isPresent() && RobinhoodBrokerRealizedPnlService.hasIndividual(stored.get().accounts())) {
            realizedYtd = stored.get().accounts().stream()
                    .filter(a -> SUFFIX.equals(a.suffix()))
                    .map(FinanceTaxDeskRhAccountRealizedDto::realized)
                    .findFirst()
                    .orElse(stored.get().total());
        } else {
            warnings.add("No stored Robinhood YTD realized for Individual yet. Open Tax desk once to cache it.");
        }
        Optional<RobinhoodRhDailySnapshot> snap =
                snapshotRepository.findTopByOwnerUserIdAndAccountSuffixOrderBySnapshotAtDescIdDesc(owner, SUFFIX);
        BigDecimal accountValue = null;
        BigDecimal equityValue = null;
        BigDecimal cash = null;
        List<RobinhoodYtdCheckPositionDto> positions = List.of();
        BigDecimal openUnrealized = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (snap.isPresent()) {
            RobinhoodRhDailySnapshot row = snap.get();
            accountValue = scale(row.getTotalAccountValue());
            equityValue = scale(row.getEquityMarketValue());
            cash = scale(row.getCashBalance());
            positions = positionsFromHoldings(row.getHoldingsJson(), equityValue);
            openUnrealized = positions.stream()
                    .map(RobinhoodYtdCheckPositionDto::unrealized)
                    .filter(v -> v != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal implied = realizedYtd == null ? null : realizedYtd.add(openUnrealized).setScale(2, RoundingMode.HALF_UP);
        return new RobinhoodYtdCheckDto(
                SUFFIX,
                LABEL,
                year,
                asOf,
                Instant.now(),
                false,
                "STORED",
                accountValue,
                equityValue,
                cash,
                null,
                realizedYtd,
                null,
                null,
                null,
                null,
                null,
                0,
                openUnrealized,
                implied,
                NOTE,
                positions,
                List.of(),
                warnings);
    }

    private List<RobinhoodYtdCheckPositionDto> positionsFromHoldings(String json, BigDecimal equityValue) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<RobinhoodRhHoldingDto> holdings = objectMapper.readValue(json, new TypeReference<>() {});
            List<JsonNode> raw = new ArrayList<>();
            for (RobinhoodRhHoldingDto h : holdings) {
                if (h == null || h.symbol() == null || h.symbol().isBlank()) {
                    continue;
                }
                raw.add(objectMapper.valueToTree(h));
            }
            return readPositions(objectMapper.valueToTree(raw), equityValue);
        } catch (Exception e) {
            log.warn("Could not read snapshot holdings for YTD check: {}", e.toString());
            return List.of();
        }
    }

    static List<RobinhoodYtdCheckPositionDto> readPositions(JsonNode node, BigDecimal equityValue) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<RobinhoodYtdCheckPositionDto> out = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        int remainingMarks = 0;
        for (JsonNode row : node) {
            if (row != null && row.isObject() && text(row, "symbol") != null) {
                remainingMarks++;
            }
        }
        int index = 0;
        for (JsonNode row : node) {
            if (row == null || !row.isObject()) {
                continue;
            }
            String symbol = text(row, "symbol");
            if (symbol == null) {
                continue;
            }
            BigDecimal qty = decimal(row, "quantity");
            BigDecimal avg = decimal(row, "average_buy_price", "averageBuyPrice");
            BigDecimal cost = qty != null && avg != null
                    ? qty.multiply(avg).setScale(2, RoundingMode.HALF_UP)
                    : decimal(row, "cost_basis", "costBasis");
            BigDecimal unrealized = decimal(row, "unrealized_pnl", "unrealizedPnL", "unrealized");
            if (unrealized == null && cost != null && equityValue != null && remainingMarks > 0) {
                if (node.size() == 1 || index == remainingMarks - 1) {
                    unrealized = equityValue.subtract(cost).subtract(allocated).setScale(2, RoundingMode.HALF_UP);
                } else {
                    unrealized = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                }
            }
            if (unrealized != null) {
                allocated = allocated.add(unrealized);
            }
            out.add(new RobinhoodYtdCheckPositionDto(
                    symbol.toUpperCase(),
                    qty,
                    avg,
                    cost,
                    unrealized));
            index++;
        }
        return List.copyOf(out);
    }

    static List<RobinhoodYtdCheckCloseDto> readCloses(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<RobinhoodYtdCheckCloseDto> out = new ArrayList<>();
        for (JsonNode row : node) {
            if (row == null || !row.isObject()) {
                continue;
            }
            String symbol = text(row, "symbol");
            if (symbol == null) {
                continue;
            }
            out.add(new RobinhoodYtdCheckCloseDto(
                    text(row, "timestamp"),
                    symbol.toUpperCase(),
                    text(row, "side"),
                    text(row, "quantity"),
                    decimal(row, "price"),
                    decimal(row, "realized")));
        }
        return List.copyOf(out);
    }

    private static List<String> readWarnings(JsonNode root) {
        List<String> warnings = new ArrayList<>();
        if (root == null) {
            return warnings;
        }
        JsonNode node = root.get("warnings");
        if (node != null && node.isArray()) {
            for (JsonNode w : node) {
                if (w != null && w.isTextual() && !w.asText().isBlank()) {
                    warnings.add(w.asText().trim());
                }
            }
        }
        return warnings;
    }

    private static Optional<LocalDate> textDate(JsonNode node, String field) {
        String raw = text(node, field);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(raw));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.isValueNode()) {
            return null;
        }
        String t = v.asText();
        return t == null || t.isBlank() ? null : t.trim();
    }

    private static BigDecimal decimal(JsonNode node, String... fields) {
        if (node == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode v = node.get(field);
            if (v == null || v.isNull() || v.isMissingNode()) {
                continue;
            }
            if (v.isNumber()) {
                return v.decimalValue().setScale(2, RoundingMode.HALF_UP);
            }
            if (v.isTextual()) {
                String t = v.asText().trim();
                if (t.isEmpty()) {
                    continue;
                }
                try {
                    return new BigDecimal(t).setScale(2, RoundingMode.HALF_UP);
                } catch (NumberFormatException ignored) {
                    // try next field
                }
            }
        }
        return null;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
