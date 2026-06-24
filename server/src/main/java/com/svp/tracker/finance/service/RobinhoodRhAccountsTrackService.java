package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.finance.domain.RobinhoodAccountTrackerConfig;
import com.svp.tracker.finance.domain.RobinhoodAgenticConnection;
import com.svp.tracker.finance.domain.RobinhoodAgenticPosition;
import com.svp.tracker.finance.dto.RobinhoodRhAccountSummaryDto;
import com.svp.tracker.finance.dto.RobinhoodRhAccountsTrackDto;
import com.svp.tracker.finance.dto.RobinhoodRhCashFlowEventDto;
import com.svp.tracker.finance.dto.RobinhoodRhHoldingDto;
import com.svp.tracker.finance.repository.RobinhoodAccountTrackerConfigRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticConnectionRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticPositionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** All Robinhood accounts: CSV cash flows (individual) + synced holdings/portfolio since Apr 5 2026. */
@Service
@RequiredArgsConstructor
@Slf4j
public class RobinhoodRhAccountsTrackService {

    private static final ZoneId CENTRAL = ZoneId.of("America/Chicago");
    private static final Instant DEFAULT_TRACKING_START =
            ZonedDateTime.of(2026, 4, 5, 0, 0, 0, 0, CENTRAL).toInstant();

    private final RobinhoodAccountTrackerConfigRepository configRepository;
    private final RobinhoodAgenticConnectionRepository connectionRepository;
    private final RobinhoodAgenticPositionRepository positionRepository;
    private final RobinhoodFinanceService financeService;
    private final FinanceProperties financeProperties;
    private final CurrentUserService currentUser;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public RobinhoodRhAccountsTrackDto build() {
        long ownerUserId = currentUser.requireUserId();
        RobinhoodAccountTrackerConfig config = getOrCreateConfig(ownerUserId);
        Instant trackingStartedAt = resolveTrackingStart(config);
        String individualSuffix = config.getIndividualAccountSuffix();
        String agenticSuffix = config.getAgenticAccountSuffix();

        int rowCap = Math.max(financeProperties.maxTransactionRows(), 2_000);
        List<Map<String, Object>> csvRows = financeService.fetchCashFlowMapsSince(trackingStartedAt, rowCap);
        List<RobinhoodRhCashFlowEventDto> individualCashFlows = extractCashFlowEvents(csvRows);

        List<RobinhoodAgenticPosition> allPositions =
                positionRepository.findByOwnerUserIdOrderBySymbolAsc(ownerUserId);
        Map<String, List<RobinhoodAgenticPosition>> positionsByAccount = groupByAccount(allPositions);

        Optional<RobinhoodAgenticConnection> connectionOpt = connectionRepository.findByOwnerUserId(ownerUserId);
        Map<String, JsonNode> portfoliosByAccount = parsePortfolios(connectionOpt);
        Instant portfolioSyncedAt = connectionOpt.map(RobinhoodAgenticConnection::getLastSyncAt).orElse(null);
        String agenticNickname = connectionOpt.map(RobinhoodAgenticConnection::getAgenticNickname).orElse(null);

        LinkedHashSet<String> accountNumbers = new LinkedHashSet<>();
        accountNumbers.addAll(portfoliosByAccount.keySet());
        accountNumbers.addAll(positionsByAccount.keySet());
        resolveAccountBySuffix(allPositions, individualSuffix).ifPresent(accountNumbers::add);
        resolveAccountBySuffix(allPositions, agenticSuffix).ifPresent(accountNumbers::add);

        List<String> sortedAccounts = sortAccounts(accountNumbers, individualSuffix, agenticSuffix, allPositions);

        List<RobinhoodRhAccountSummaryDto> accounts = new ArrayList<>();
        for (String accountNumber : sortedAccounts) {
            accounts.add(
                    buildAccountSummary(
                            accountNumber,
                            individualSuffix,
                            agenticSuffix,
                            agenticNickname,
                            individualCashFlows,
                            positionsByAccount.getOrDefault(accountNumber, List.of()),
                            portfoliosByAccount.get(accountNumber),
                            portfolioSyncedAt));
        }

        if (accounts.isEmpty()) {
            accounts.add(
                    buildPlaceholderIndividual(
                            individualSuffix, trackingStartedAt, individualCashFlows));
        }

        BigDecimal combinedValue = BigDecimal.ZERO;
        BigDecimal combinedNetFlow = BigDecimal.ZERO;
        BigDecimal combinedGain = BigDecimal.ZERO;
        for (RobinhoodRhAccountSummaryDto acct : accounts) {
            combinedValue = combinedValue.add(nullToZero(acct.totalAccountValue()));
            combinedNetFlow = combinedNetFlow.add(nullToZero(acct.netCashFlow()));
            combinedGain = combinedGain.add(nullToZero(acct.gainLossVsNetDeposits()));
        }

        List<String> notes = buildGlobalNotes(csvRows.size(), rowCap, connectionOpt, accounts);

        return new RobinhoodRhAccountsTrackDto(
                trackingStartedAt,
                accounts,
                scaleMoney(combinedValue),
                scaleMoney(combinedNetFlow),
                scaleMoney(combinedGain),
                combinedGain.compareTo(BigDecimal.ZERO) >= 0,
                notes);
    }

    private RobinhoodRhAccountSummaryDto buildPlaceholderIndividual(
            String individualSuffix,
            Instant trackingStartedAt,
            List<RobinhoodRhCashFlowEventDto> cashFlows) {
        CashFlowTotals flow = summarizeCashFlows(cashFlows);
        List<String> notes = List.of(
                "No synced Robinhood accounts yet — showing CSV cash flows for individual ••••"
                        + individualSuffix
                        + " only. Connect and sync Agentic Trading for live holdings.");
        return new RobinhoodRhAccountSummaryDto(
                maskSuffix(individualSuffix),
                individualSuffix,
                "Individual " + maskSuffix(individualSuffix),
                false,
                flow.deposits,
                flow.withdrawals,
                flow.net,
                cashFlows,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                flow.net.negate(),
                flow.net.compareTo(BigDecimal.ZERO) <= 0,
                List.of(),
                null,
                notes);
    }

    private RobinhoodRhAccountSummaryDto buildAccountSummary(
            String accountNumber,
            String individualSuffix,
            String agenticSuffix,
            String agenticNickname,
            List<RobinhoodRhCashFlowEventDto> individualCashFlows,
            List<RobinhoodAgenticPosition> positions,
            JsonNode portfolioNode,
            Instant syncedAt) {
        boolean individual = accountEndsWith(accountNumber, individualSuffix);
        boolean agentic = accountEndsWith(accountNumber, agenticSuffix);
        String suffix = accountNumber.length() >= 4 ? accountNumber.substring(accountNumber.length() - 4) : accountNumber;
        String label = accountLabel(individual, agentic, suffix, agenticNickname);

        List<RobinhoodRhCashFlowEventDto> cashFlows = individual ? individualCashFlows : List.of();
        CashFlowTotals flow = summarizeCashFlows(cashFlows);

        List<RobinhoodRhHoldingDto> holdings = buildHoldings(positions);
        HoldingsTotals holdingsTotals = summarizeHoldings(holdings);

        PortfolioTotals portfolio = parsePortfolio(portfolioNode);
        BigDecimal cash = portfolio.cash != null ? portfolio.cash : BigDecimal.ZERO;
        BigDecimal equityMv =
                portfolio.equityValue != null ? portfolio.equityValue : holdingsTotals.marketValue;
        BigDecimal totalValue = portfolio.totalValue;
        if (totalValue == null || totalValue.compareTo(BigDecimal.ZERO) == 0) {
            totalValue = equityMv.add(cash);
        }

        BigDecimal gainLossVsNetDeposits;
        boolean gainLossPositive;
        if (individual && flow.net.compareTo(BigDecimal.ZERO) != 0) {
            gainLossVsNetDeposits = totalValue.subtract(flow.net);
            gainLossPositive = gainLossVsNetDeposits.compareTo(BigDecimal.ZERO) >= 0;
        } else if (!individual) {
            gainLossVsNetDeposits = holdingsTotals.unrealizedPnL;
            gainLossPositive = gainLossVsNetDeposits.compareTo(BigDecimal.ZERO) >= 0;
        } else {
            gainLossVsNetDeposits = totalValue.subtract(flow.net);
            gainLossPositive = gainLossVsNetDeposits.compareTo(BigDecimal.ZERO) >= 0;
        }

        Instant accountSyncedAt = syncedAt;
        for (RobinhoodAgenticPosition p : positions) {
            if (p.getSyncedAt() != null && (accountSyncedAt == null || p.getSyncedAt().isAfter(accountSyncedAt))) {
                accountSyncedAt = p.getSyncedAt();
            }
        }

        List<String> notes = new ArrayList<>();
        if (!individual && !cashFlows.isEmpty()) {
            notes.add("Cash flows from CSV import apply to the individual account only.");
        }
        if (!individual && cashFlows.isEmpty()) {
            notes.add("Deposits/withdrawals not available for this account (CSV tracks individual ••••"
                    + individualSuffix
                    + "). Gain/loss shown as unrealized P&L on open positions.");
        }
        if (portfolioNode == null && !positions.isEmpty()) {
            notes.add("Portfolio totals estimated from synced positions (no portfolio snapshot for this account).");
        }

        return new RobinhoodRhAccountSummaryDto(
                maskSuffix(suffix),
                suffix,
                label,
                agentic,
                flow.deposits,
                flow.withdrawals,
                flow.net,
                cashFlows,
                scaleMoney(cash),
                scaleMoney(equityMv),
                scaleMoney(totalValue),
                scaleMoney(holdingsTotals.costBasis),
                scaleMoney(holdingsTotals.unrealizedPnL),
                scaleMoney(gainLossVsNetDeposits),
                gainLossPositive,
                holdings,
                accountSyncedAt,
                notes);
    }

    private static List<RobinhoodRhHoldingDto> buildHoldings(List<RobinhoodAgenticPosition> positions) {
        List<RobinhoodRhHoldingDto> out = new ArrayList<>();
        for (RobinhoodAgenticPosition p : positions) {
            BigDecimal qty = nullToZero(p.getQuantity());
            if (qty.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            BigDecimal avg = nullToZero(p.getAverageBuyPrice());
            BigDecimal mv = nullToZero(p.getMarketValue());
            BigDecimal cost = qty.abs().multiply(avg).setScale(2, RoundingMode.HALF_UP);
            BigDecimal unrealized = mv.subtract(cost).setScale(2, RoundingMode.HALF_UP);
            out.add(
                    new RobinhoodRhHoldingDto(
                            p.getSymbol(),
                            p.getPositionType(),
                            qty,
                            avg,
                            mv,
                            cost,
                            unrealized));
        }
        out.sort(Comparator.comparing(RobinhoodRhHoldingDto::symbol, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private static HoldingsTotals summarizeHoldings(List<RobinhoodRhHoldingDto> holdings) {
        BigDecimal marketValue = BigDecimal.ZERO;
        BigDecimal costBasis = BigDecimal.ZERO;
        BigDecimal unrealized = BigDecimal.ZERO;
        for (RobinhoodRhHoldingDto h : holdings) {
            marketValue = marketValue.add(nullToZero(h.marketValue()));
            costBasis = costBasis.add(nullToZero(h.costBasis()));
            unrealized = unrealized.add(nullToZero(h.unrealizedPnL()));
        }
        return new HoldingsTotals(marketValue, costBasis, unrealized);
    }

    private List<RobinhoodRhCashFlowEventDto> extractCashFlowEvents(List<Map<String, Object>> rows) {
        List<RobinhoodRhCashFlowEventDto> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String transCode = stringCell(row, "TRANS_CODE", "trans_code");
            String description = stringCell(row, "DESCRIPTION", "description");
            String instrument = stringCell(row, "INSTRUMENT", "instrument");
            if (!RobinhoodCashFlowClassifier.isCashFlowRow(transCode, description, instrument)) {
                continue;
            }
            BigDecimal amount = decimalCell(row, "AMOUNT", "amount");
            if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            LocalDate activityDate = localDateCell(row, "ACTIVITY_DATE", "activity_date");
            String direction = RobinhoodCashFlowClassifier.cashFlowDirection(transCode, description, amount);
            if ("OTHER".equals(direction)) {
                continue;
            }
            out.add(
                    new RobinhoodRhCashFlowEventDto(
                            activityDate,
                            direction,
                            scaleMoney(amount.abs()),
                            RobinhoodCashFlowClassifier.displayFlowType(transCode, description),
                            trimOrNull(description),
                            "CSV"));
        }
        return out;
    }

    private static CashFlowTotals summarizeCashFlows(List<RobinhoodRhCashFlowEventDto> events) {
        BigDecimal deposits = BigDecimal.ZERO;
        BigDecimal withdrawals = BigDecimal.ZERO;
        for (RobinhoodRhCashFlowEventDto e : events) {
            if ("IN".equals(e.direction())) {
                deposits = deposits.add(nullToZero(e.amount()));
            } else if ("OUT".equals(e.direction())) {
                withdrawals = withdrawals.add(nullToZero(e.amount()));
            }
        }
        BigDecimal net = deposits.subtract(withdrawals).setScale(2, RoundingMode.HALF_UP);
        return new CashFlowTotals(
                scaleMoney(deposits), scaleMoney(withdrawals), net);
    }

    private Map<String, JsonNode> parsePortfolios(Optional<RobinhoodAgenticConnection> connectionOpt) {
        Map<String, JsonNode> out = new LinkedHashMap<>();
        if (connectionOpt.isEmpty()) {
            return out;
        }
        String json = connectionOpt.get().getPortfolioJson();
        if (json == null || json.isBlank()) {
            return out;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isObject()) {
                return out;
            }
            root.fields()
                    .forEachRemaining(
                            entry -> {
                                if (entry.getKey() != null && !entry.getKey().isBlank()) {
                                    out.put(entry.getKey().trim(), entry.getValue());
                                }
                            });
        } catch (Exception e) {
            log.warn("Could not parse portfolio_json: {}", e.getMessage());
        }
        return out;
    }

    private static Map<String, List<RobinhoodAgenticPosition>> groupByAccount(
            List<RobinhoodAgenticPosition> positions) {
        Map<String, List<RobinhoodAgenticPosition>> out = new LinkedHashMap<>();
        for (RobinhoodAgenticPosition p : positions) {
            String acct = p.getAccountNumber() == null ? "" : p.getAccountNumber().trim();
            if (acct.isEmpty()) {
                continue;
            }
            out.computeIfAbsent(acct, k -> new ArrayList<>()).add(p);
        }
        return out;
    }

    private static List<String> sortAccounts(
            LinkedHashSet<String> accountNumbers,
            String individualSuffix,
            String agenticSuffix,
            List<RobinhoodAgenticPosition> allPositions) {
        List<String> list = new ArrayList<>(accountNumbers);
        list.sort(
                Comparator.<String>comparingInt(
                                acct -> accountSortRank(acct, individualSuffix, agenticSuffix))
                        .thenComparing(acct -> suffixOf(acct, allPositions)));
        return list;
    }

    private static int accountSortRank(String accountNumber, String individualSuffix, String agenticSuffix) {
        if (accountEndsWith(accountNumber, individualSuffix)) {
            return 0;
        }
        if (accountEndsWith(accountNumber, agenticSuffix)) {
            return 1;
        }
        return 2;
    }

    private static Optional<String> resolveAccountBySuffix(
            List<RobinhoodAgenticPosition> positions, String suffix) {
        for (RobinhoodAgenticPosition p : positions) {
            if (accountEndsWith(p.getAccountNumber(), suffix)) {
                return Optional.of(p.getAccountNumber().trim());
            }
        }
        return Optional.empty();
    }

    private static String suffixOf(String accountNumber, List<RobinhoodAgenticPosition> allPositions) {
        if (accountNumber != null && accountNumber.length() >= 4) {
            return accountNumber.substring(accountNumber.length() - 4);
        }
        return accountNumber == null ? "" : accountNumber;
    }

    private static String accountLabel(
            boolean individual, boolean agentic, String suffix, String agenticNickname) {
        if (individual) {
            return "Individual " + maskSuffix(suffix);
        }
        if (agentic) {
            String nick = agenticNickname == null ? "" : agenticNickname.trim();
            if (!nick.isBlank()) {
                return "Agentic " + maskSuffix(suffix) + " · " + nick;
            }
            return "Agentic " + maskSuffix(suffix);
        }
        return "Account " + maskSuffix(suffix);
    }

    private static PortfolioTotals parsePortfolio(JsonNode portfolioNode) {
        if (portfolioNode == null || portfolioNode.isNull()) {
            return PortfolioTotals.empty();
        }
        JsonNode data = portfolioNode.path("data");
        if (data.isMissingNode() || !data.isObject()) {
            data = portfolioNode;
        }
        BigDecimal total = firstDecimal(data, "total_value", "total_equity", "portfolio_value", "equity");
        BigDecimal cash = firstDecimal(data, "cash", "uninvested_cash");
        BigDecimal equity = firstDecimal(data, "equity_value", "market_value", "extended_hours_equity");
        return new PortfolioTotals(total, cash, equity);
    }

    private static BigDecimal firstDecimal(JsonNode node, String... fields) {
        for (String field : fields) {
            BigDecimal v = decimal(node, field);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.isNumber()) {
            return BigDecimal.valueOf(v.asDouble()).setScale(2, RoundingMode.HALF_UP);
        }
        String text = v.asText("").trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<String> buildGlobalNotes(
            int csvRowCount,
            int rowCap,
            Optional<RobinhoodAgenticConnection> connectionOpt,
            List<RobinhoodRhAccountSummaryDto> accounts) {
        List<String> notes = new ArrayList<>();
        notes.add(
                "Tracking window starts Apr 5, 2026 00:00 Central. Cash flows include Transfer, Transfer In/Out, ACH, ITRF, RTP, and similar CSV rows on the individual account; synced MCP data covers all connected accounts.");
        if (csvRowCount >= rowCap) {
            notes.add("Cash-flow query capped at " + rowCap + " rows; older transfers since cutoff may be omitted.");
        }
        if (connectionOpt.isEmpty() || connectionOpt.get().getLastSyncAt() == null) {
            notes.add("Connect Robinhood Agentic Trading and run Sync for live holdings and portfolio totals.");
        }
        long withoutHoldings =
                accounts.stream().filter(a -> a.holdings().isEmpty() && a.totalAccountValue().signum() == 0).count();
        if (withoutHoldings > 0 && connectionOpt.isPresent()) {
            notes.add(withoutHoldings + " account(s) have no synced holdings — re-sync or check account selection.");
        }
        return notes;
    }

    private RobinhoodAccountTrackerConfig getOrCreateConfig(long ownerUserId) {
        return configRepository
                .findByOwnerUserId(ownerUserId)
                .map(this::ensureRhTrackStart)
                .orElseGet(() -> createDefaultConfig(ownerUserId));
    }

    private RobinhoodAccountTrackerConfig ensureRhTrackStart(RobinhoodAccountTrackerConfig config) {
        if (config.getRhAccountsTrackStartedAt() == null) {
            config.setRhAccountsTrackStartedAt(DEFAULT_TRACKING_START);
            config.setUpdatedAt(Instant.now());
            return configRepository.save(config);
        }
        return config;
    }

    private RobinhoodAccountTrackerConfig createDefaultConfig(long ownerUserId) {
        Instant now = Instant.now();
        Instant nbisTrackingStart = ZonedDateTime.of(2026, 6, 24, 0, 0, 0, 0, CENTRAL).toInstant();

        RobinhoodAccountTrackerConfig config = new RobinhoodAccountTrackerConfig();
        config.setOwnerUserId(ownerUserId);
        config.setTrackingStartedAt(nbisTrackingStart);
        config.setRhAccountsTrackStartedAt(DEFAULT_TRACKING_START);
        config.setIndividualAccountSuffix("3370");
        config.setIndividualBaselineNbis(new BigDecimal("732"));
        config.setAgenticAccountSuffix("3550");
        config.setCreatedAt(now);
        config.setUpdatedAt(now);
        return configRepository.save(config);
    }

    private static Instant resolveTrackingStart(RobinhoodAccountTrackerConfig config) {
        return config.getRhAccountsTrackStartedAt() != null
                ? config.getRhAccountsTrackStartedAt()
                : DEFAULT_TRACKING_START;
    }

    private static boolean accountEndsWith(String accountNumber, String suffix) {
        if (accountNumber == null || suffix == null || suffix.isBlank()) {
            return false;
        }
        return accountNumber.trim().endsWith(suffix);
    }

    private static String maskSuffix(String suffix) {
        return "••••" + (suffix == null ? "" : suffix);
    }

    private static BigDecimal scaleMoney(BigDecimal v) {
        if (v == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return v.setScale(2, RoundingMode.HALF_UP);
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
                // try next
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

    private record CashFlowTotals(BigDecimal deposits, BigDecimal withdrawals, BigDecimal net) {}

    private record HoldingsTotals(BigDecimal marketValue, BigDecimal costBasis, BigDecimal unrealizedPnL) {}

    private record PortfolioTotals(BigDecimal totalValue, BigDecimal cash, BigDecimal equityValue) {
        static PortfolioTotals empty() {
            return new PortfolioTotals(null, null, null);
        }
    }
}
