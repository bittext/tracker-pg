package com.svp.tracker.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.FinanceProperties;
import com.svp.tracker.finance.domain.RobinhoodAccountTrackerConfig;
import com.svp.tracker.finance.domain.RobinhoodAgenticConnection;
import com.svp.tracker.finance.domain.RobinhoodAgenticPosition;
import com.svp.tracker.finance.domain.RobinhoodRhAccountStartingBalance;
import com.svp.tracker.finance.domain.RobinhoodRhSupplementalCashFlow;
import com.svp.tracker.finance.dto.RobinhoodRhAccountSummaryDto;
import com.svp.tracker.finance.dto.RobinhoodRhAccountsTrackDto;
import com.svp.tracker.finance.dto.RobinhoodRhCashFlowEventDto;
import com.svp.tracker.finance.dto.RobinhoodRhHoldingDto;
import com.svp.tracker.finance.dto.RobinhoodRhLiveQuotesDto;
import com.svp.tracker.finance.repository.RobinhoodAccountTrackerConfigRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticConnectionRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticPositionRepository;
import com.svp.tracker.finance.repository.RobinhoodRhAccountStartingBalanceRepository;
import com.svp.tracker.finance.repository.RobinhoodRhSupplementalCashFlowRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
    private final RobinhoodRhSupplementalCashFlowRepository supplementalCashFlowRepository;
    private final RobinhoodRhAccountStartingBalanceRepository startingBalanceRepository;
    private final RobinhoodAgenticConnectionRepository connectionRepository;
    private final RobinhoodAgenticPositionRepository positionRepository;
    private final RobinhoodFinanceService financeService;
    private final RobinhoodAgenticService agenticService;
    private final FinanceProperties financeProperties;
    private final CurrentUserService currentUser;
    private final YahooBatchQuoteService yahooBatchQuoteService;
    private final RobinhoodRhHoldingQuoteService holdingQuoteService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public RobinhoodRhAccountsTrackDto build() {
        return build(true);
    }

    @Transactional
    public RobinhoodRhAccountsTrackDto build(boolean syncLatest) {
        return buildForOwner(currentUser.requireUserId(), syncLatest);
    }

    @Transactional
    public RobinhoodRhAccountsTrackDto buildForOwner(long ownerUserId, boolean syncLatest) {
        List<String> syncNotes = List.of();
        if (syncLatest) {
            syncNotes = agenticService.syncLatestForAccountsTrack();
        }
        RobinhoodAccountTrackerConfig config = getOrCreateConfig(ownerUserId);
        Instant trackingStartedAt = resolveTrackingStart(config);
        String individualSuffix = config.getIndividualAccountSuffix();
        String agenticSuffix = config.getAgenticAccountSuffix();
        String managedSuffix = trimOrNull(config.getManagedAccountSuffix());

        int rowCap = Math.max(financeProperties.maxTransactionRows(), 2_000);
        List<Map<String, Object>> csvRows = financeService.fetchCashFlowMapsSinceForOwner(ownerUserId, trackingStartedAt, rowCap);
        List<RobinhoodRhCashFlowEventDto> rawIndividualFlows = extractCashFlowEvents(csvRows);

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
        if (managedSuffix != null) {
            resolveAccountBySuffix(allPositions, managedSuffix).ifPresent(accountNumbers::add);
        }

        Set<String> knownSuffixes =
                RobinhoodRhCashFlowAllocator.collectKnownSuffixes(
                        accountNumbers, individualSuffix, agenticSuffix, managedSuffix);
        Map<String, List<RobinhoodRhCashFlowEventDto>> flowsBySuffix =
                RobinhoodRhCashFlowAllocator.allocateByAccountSuffix(
                        rawIndividualFlows, individualSuffix, agenticSuffix, managedSuffix, knownSuffixes);
        mergeSupplementalCashFlows(flowsBySuffix, ownerUserId);

        LocalDate trackingStartDate = trackingStartedAt.atZone(CENTRAL).toLocalDate();
        LinkedHashSet<String> trackedSuffixes =
                collectTrackedSuffixes(
                        accountNumbers, individualSuffix, agenticSuffix, managedSuffix, allPositions, flowsBySuffix);
        Map<String, BigDecimal> startingBySuffix =
                resolveStartingBalances(ownerUserId, config, trackedSuffixes);
        prependStartingBalances(flowsBySuffix, trackingStartDate, startingBySuffix);

        List<String> sortedAccounts = sortAccounts(accountNumbers, individualSuffix, agenticSuffix, managedSuffix, allPositions);
        if (managedSuffix != null && sortedAccounts.stream().noneMatch(a -> accountEndsWith(a, managedSuffix))) {
            sortedAccounts.add(managedSuffix);
        }

        List<RobinhoodRhAccountSummaryDto> accounts = new ArrayList<>();
        for (String accountKey : sortedAccounts) {
            String suffix = resolveSuffix(accountKey, allPositions);
            accounts.add(
                    buildAccountSummary(
                            ownerUserId,
                            accountKey,
                            suffix,
                            individualSuffix,
                            agenticSuffix,
                            managedSuffix,
                            agenticNickname,
                            flowsBySuffix.getOrDefault(suffix, List.of()),
                            resolvePositions(accountKey, suffix, positionsByAccount),
                            portfoliosByAccount.get(resolveFullAccountNumber(accountKey, portfoliosByAccount.keySet())),
                            portfolioSyncedAt,
                            startingBySuffix.getOrDefault(suffix, BigDecimal.ZERO)));
        }

        if (accounts.isEmpty()) {
            List<RobinhoodRhCashFlowEventDto> individualFlows =
                    flowsBySuffix.getOrDefault(individualSuffix, List.of());
            accounts.add(
                    buildPlaceholderIndividual(
                            individualSuffix,
                            individualFlows,
                            startingBySuffix.getOrDefault(individualSuffix, BigDecimal.ZERO)));
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
        notes.addAll(0, syncNotes);

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
            List<RobinhoodRhCashFlowEventDto> cashFlows,
            BigDecimal startingTotal) {
        List<String> notes = List.of(
                "No synced Robinhood accounts yet — showing CSV cash flows for individual ••••"
                        + individualSuffix
                        + " only. Connect and sync Agentic Trading for live holdings.");
        FlowTotals flow = summarizeFlows(cashFlows, startingTotal);
        BigDecimal basis = flow.net;
        BigDecimal gainLoss = BigDecimal.ZERO.subtract(basis);
        return buildSummaryFromParts(
                maskSuffix(individualSuffix),
                individualSuffix,
                "Individual " + maskSuffix(individualSuffix),
                "INDIVIDUAL",
                false,
                false,
                startingTotal,
                flow,
                cashFlows,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                scaleMoney(gainLoss),
                gainLoss.compareTo(BigDecimal.ZERO) >= 0,
                List.of(),
                null,
                notes);
    }

    private RobinhoodRhAccountSummaryDto buildAccountSummary(
            long ownerUserId,
            String accountNumber,
            String suffix,
            String individualSuffix,
            String agenticSuffix,
            String managedSuffix,
            String agenticNickname,
            List<RobinhoodRhCashFlowEventDto> cashFlows,
            List<RobinhoodAgenticPosition> positions,
            JsonNode portfolioNode,
            Instant syncedAt,
            BigDecimal startingTotal) {
        boolean individual = accountEndsWith(accountNumber, individualSuffix) || suffix.equals(individualSuffix);
        boolean agentic = accountEndsWith(accountNumber, agenticSuffix) || suffix.equals(agenticSuffix);
        boolean managed = managedSuffix != null
                && (accountEndsWith(accountNumber, managedSuffix) || suffix.equals(managedSuffix));
        String label = accountLabel(individual, agentic, managed, suffix, agenticNickname);
        String accountKind = accountKind(individual, agentic, managed);

        FlowTotals flow = summarizeFlows(cashFlows, startingTotal);

        PortfolioTotals portfolio = parsePortfolio(portfolioNode);
        BigDecimal cash = portfolio.cash != null ? portfolio.cash : BigDecimal.ZERO;
        BigDecimal equityMv =
                portfolio.equityValue != null ? portfolio.equityValue : BigDecimal.ZERO;

        RobinhoodRhLiveQuotesDto liveQuotes =
                holdingQuoteService.fetchForHoldings(ownerUserId, List.of(), positions);
        List<RobinhoodRhHoldingDto> holdings = RobinhoodRhHoldingValues.fromPositions(
                positions, equityMv, yahooBatchQuoteService, liveQuotes);
        HoldingsTotals holdingsTotals = summarizeHoldings(holdings);
        if (portfolio.equityValue == null) {
            equityMv = holdingsTotals.marketValue;
        }
        BigDecimal totalValue = portfolio.totalValue;
        if (totalValue == null || totalValue.compareTo(BigDecimal.ZERO) == 0) {
            totalValue = equityMv.add(cash);
        }

        BigDecimal basis = flow.net;
        BigDecimal gainLossVsNetDeposits = totalValue.subtract(basis);
        boolean gainLossPositive = gainLossVsNetDeposits.compareTo(BigDecimal.ZERO) >= 0;

        Instant accountSyncedAt = syncedAt;
        for (RobinhoodAgenticPosition p : positions) {
            if (p.getSyncedAt() != null && (accountSyncedAt == null || p.getSyncedAt().isAfter(accountSyncedAt))) {
                accountSyncedAt = p.getSyncedAt();
            }
        }

        List<String> notes = new ArrayList<>();
        if (individual) {
            notes.add("CSV cash flows originate on this account; internal transfers out to Agentic, Managed, or other RH accounts are classified as Out and mirrored on the destination account.");
        } else if (agentic) {
            notes.add("Funding from the individual account appears as Internal transfer In (derived from CSV when Robinhood posts ITRF/Transfer out on ••••"
                    + individualSuffix + ").");
        } else if (managed) {
            notes.add("Managed account: holdings and portfolio from sync; cash flows from supplemental config plus internal transfers mirrored from the individual CSV.");
        } else if (cashFlows.isEmpty()) {
            notes.add("Cash flows only when mirrored from internal transfers on the individual CSV export.");
        }
        if (portfolioNode == null && !positions.isEmpty()) {
            notes.add("Portfolio totals estimated from synced positions (no portfolio snapshot for this account).");
        }

        return buildSummaryFromParts(
                maskSuffix(suffix),
                suffix,
                label,
                accountKind,
                agentic,
                managed,
                startingTotal,
                flow,
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

    private static RobinhoodRhAccountSummaryDto buildSummaryFromParts(
            String masked,
            String suffix,
            String label,
            String accountKind,
            boolean agentic,
            boolean managed,
            BigDecimal startingTotal,
            FlowTotals flow,
            List<RobinhoodRhCashFlowEventDto> cashFlows,
            BigDecimal cash,
            BigDecimal equityMv,
            BigDecimal totalValue,
            BigDecimal costBasis,
            BigDecimal unrealizedPnL,
            BigDecimal gainLoss,
            boolean gainLossPositive,
            List<RobinhoodRhHoldingDto> holdings,
            Instant syncedAt,
            List<String> notes) {
        return new RobinhoodRhAccountSummaryDto(
                masked,
                suffix,
                label,
                accountKind,
                agentic,
                managed,
                startingTotal == null ? BigDecimal.ZERO : scaleMoney(startingTotal),
                flow.deposits,
                flow.withdrawals,
                flow.internalIn,
                flow.internalOut,
                flow.net,
                cashFlows,
                cash,
                equityMv,
                totalValue,
                costBasis,
                unrealizedPnL,
                gainLoss,
                gainLossPositive,
                holdings,
                syncedAt,
                notes);
    }

    /** Recompute market values for holdings deserialized from a stored snapshot. */
    public List<RobinhoodRhHoldingDto> finalizeSnapshotHoldings(
            long ownerUserId,
            String accountSuffix,
            List<RobinhoodRhHoldingDto> holdings,
            BigDecimal accountEquityMarketValue) {
        List<RobinhoodAgenticPosition> positions = positionsForAccountSuffix(ownerUserId, accountSuffix);
        RobinhoodRhLiveQuotesDto liveQuotes =
                holdingQuoteService.fetchForHoldings(ownerUserId, holdings, positions);
        Map<String, String> optionInstrumentIds = RobinhoodRhHoldingQuoteService.instrumentIdsByMatchKey(positions);
        return RobinhoodRhHoldingValues.finalizeHoldings(
                holdings, accountEquityMarketValue, yahooBatchQuoteService, liveQuotes, optionInstrumentIds);
    }

    private List<RobinhoodAgenticPosition> positionsForAccountSuffix(long ownerUserId, String accountSuffix) {
        if (accountSuffix == null || accountSuffix.isBlank()) {
            return List.of();
        }
        String suffix = accountSuffix.trim();
        return positionRepository.findByOwnerUserIdOrderBySymbolAsc(ownerUserId).stream()
                .filter(p -> p.getAccountNumber() != null && p.getAccountNumber().endsWith(suffix))
                .toList();
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
                            "CSV",
                            RobinhoodCashFlowClassifier.flowCategory(transCode, description, direction),
                            RobinhoodCashFlowClassifier.internalTransfer(transCode, description),
                            null));
        }
        return out;
    }

    static FlowTotals summarizeFlows(List<RobinhoodRhCashFlowEventDto> events, BigDecimal startingTotal) {
        BigDecimal deposits = BigDecimal.ZERO;
        BigDecimal withdrawals = BigDecimal.ZERO;
        BigDecimal internalIn = BigDecimal.ZERO;
        BigDecimal internalOut = BigDecimal.ZERO;
        for (RobinhoodRhCashFlowEventDto e : events) {
            if ("STARTING_BALANCE".equals(e.flowCategory())) {
                continue;
            }
            String cat = e.flowCategory() == null ? "" : e.flowCategory();
            if ("IN".equals(e.direction())) {
                deposits = deposits.add(nullToZero(e.amount()));
                if ("INTERNAL_IN".equals(cat)) {
                    internalIn = internalIn.add(nullToZero(e.amount()));
                }
            } else if ("OUT".equals(e.direction())) {
                withdrawals = withdrawals.add(nullToZero(e.amount()));
                if ("INTERNAL_OUT".equals(cat)) {
                    internalOut = internalOut.add(nullToZero(e.amount()));
                }
            }
        }
        BigDecimal net =
                nullToZero(startingTotal)
                        .add(deposits)
                        .subtract(withdrawals)
                        .setScale(2, RoundingMode.HALF_UP);
        return new FlowTotals(
                scaleMoney(deposits),
                scaleMoney(withdrawals),
                scaleMoney(internalIn),
                scaleMoney(internalOut),
                net);
    }

    private void mergeSupplementalCashFlows(
            Map<String, List<RobinhoodRhCashFlowEventDto>> flowsBySuffix, long ownerUserId) {
        for (RobinhoodRhSupplementalCashFlow row :
                supplementalCashFlowRepository.findByOwnerUserIdOrderByActivityDateAscIdAsc(ownerUserId)) {
            String suffix = trimOrNull(row.getAccountSuffix());
            if (suffix == null) {
                continue;
            }
            List<RobinhoodRhCashFlowEventDto> list = flowsBySuffix.computeIfAbsent(suffix, k -> new ArrayList<>());
            list.add(toSupplementalEvent(row));
            RobinhoodRhCashFlowAllocator.sortCashFlowEvents(list);
        }
    }

    private static RobinhoodRhCashFlowEventDto toSupplementalEvent(RobinhoodRhSupplementalCashFlow row) {
        String transCode =
                row.getTransCode() != null && !row.getTransCode().isBlank()
                        ? row.getTransCode().trim()
                        : "Cash flow";
        return new RobinhoodRhCashFlowEventDto(
                row.getActivityDate(),
                row.getDirection(),
                scaleMoney(row.getAmount()),
                transCode,
                trimOrNull(row.getDescription()),
                row.getSource() == null ? "Config" : row.getSource(),
                row.getFlowCategory(),
                "INTERNAL_IN".equals(row.getFlowCategory()) || "INTERNAL_OUT".equals(row.getFlowCategory()),
                null);
    }

    private Map<String, BigDecimal> resolveStartingBalances(
            long ownerUserId, RobinhoodAccountTrackerConfig config, Collection<String> suffixes) {
        ensureStartingBalanceRows(ownerUserId, config, suffixes);
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        for (RobinhoodRhAccountStartingBalance row :
                startingBalanceRepository.findByOwnerUserIdOrderByAccountSuffixAsc(ownerUserId)) {
            out.put(row.getAccountSuffix(), row.getStartingTotalValue());
        }
        return out;
    }

    private void ensureStartingBalanceRows(
            long ownerUserId, RobinhoodAccountTrackerConfig config, Collection<String> suffixes) {
        Set<String> existing = new HashSet<>();
        for (RobinhoodRhAccountStartingBalance row :
                startingBalanceRepository.findByOwnerUserIdOrderByAccountSuffixAsc(ownerUserId)) {
            existing.add(row.getAccountSuffix());
        }
        Instant now = Instant.now();
        List<RobinhoodRhAccountStartingBalance> newRows = new ArrayList<>();
        for (String suffix : suffixes) {
            String normalized = normalizeAccountSuffix(trimOrNull(suffix));
            if (normalized == null || normalized.isBlank() || existing.contains(normalized)) {
                continue;
            }
            RobinhoodRhAccountStartingBalance row = new RobinhoodRhAccountStartingBalance();
            row.setOwnerUserId(ownerUserId);
            row.setAccountSuffix(normalized);
            row.setStartingTotalValue(legacyStartingForSuffix(normalized, config));
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            newRows.add(row);
        }
        if (!newRows.isEmpty()) {
            startingBalanceRepository.saveAll(newRows);
        }
    }

    private static LinkedHashSet<String> collectTrackedSuffixes(
            Set<String> accountNumbers,
            String individualSuffix,
            String agenticSuffix,
            String managedSuffix,
            List<RobinhoodAgenticPosition> allPositions,
            Map<String, List<RobinhoodRhCashFlowEventDto>> flowsBySuffix) {
        LinkedHashSet<String> suffixes = new LinkedHashSet<>();
        suffixes.add(normalizeAccountSuffix(individualSuffix));
        suffixes.add(normalizeAccountSuffix(agenticSuffix));
        if (managedSuffix != null && !managedSuffix.isBlank()) {
            suffixes.add(normalizeAccountSuffix(managedSuffix.trim()));
        }
        for (String accountKey : accountNumbers) {
            suffixes.add(resolveSuffix(accountKey, allPositions));
        }
        for (String key : flowsBySuffix.keySet()) {
            suffixes.add(normalizeAccountSuffix(key));
        }
        suffixes.removeIf(s -> s == null || s.isBlank());
        return suffixes;
    }

    /** Last 4 digits of a Robinhood account number, or the value itself when already a short suffix. */
    private static String normalizeAccountSuffix(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > 4 && trimmed.chars().allMatch(Character::isDigit)) {
            return trimmed.substring(trimmed.length() - 4);
        }
        return trimmed;
    }

    private static String resolveSuffix(String accountKey, List<RobinhoodAgenticPosition> allPositions) {
        return suffixOf(accountKey, allPositions);
    }

    private static BigDecimal legacyStartingForSuffix(String suffix, RobinhoodAccountTrackerConfig config) {
        if (suffix.equals(config.getIndividualAccountSuffix())) {
            return config.getIndividualStartingTotalValue() != null
                    ? config.getIndividualStartingTotalValue()
                    : BigDecimal.ZERO;
        }
        if (suffix.equals(config.getAgenticAccountSuffix())) {
            return config.getAgenticStartingTotalValue() != null
                    ? config.getAgenticStartingTotalValue()
                    : BigDecimal.ZERO;
        }
        String managed = trimOrNull(config.getManagedAccountSuffix());
        if (managed != null && suffix.equals(managed)) {
            return config.getManagedStartingTotalValue() != null
                    ? config.getManagedStartingTotalValue()
                    : BigDecimal.ZERO;
        }
        return BigDecimal.ZERO;
    }

    private static void prependStartingBalances(
            Map<String, List<RobinhoodRhCashFlowEventDto>> flowsBySuffix,
            LocalDate trackingStartDate,
            Map<String, BigDecimal> startingBySuffix) {
        for (Map.Entry<String, BigDecimal> entry : startingBySuffix.entrySet()) {
            putStartingBalance(flowsBySuffix, entry.getKey(), trackingStartDate, entry.getValue());
        }
    }

    private static void putStartingBalance(
            Map<String, List<RobinhoodRhCashFlowEventDto>> flowsBySuffix,
            String suffix,
            LocalDate asOf,
            BigDecimal amount) {
        if (suffix == null || suffix.isBlank()) {
            return;
        }
        BigDecimal value = amount != null ? amount : BigDecimal.ZERO;
        List<RobinhoodRhCashFlowEventDto> list = flowsBySuffix.computeIfAbsent(suffix, k -> new ArrayList<>());
        list.add(0, RobinhoodRhCashFlowAllocator.startingBalanceEvent(asOf, value));
    }

    private static List<RobinhoodAgenticPosition> resolvePositions(
            String accountKey,
            String suffix,
            Map<String, List<RobinhoodAgenticPosition>> positionsByAccount) {
        if (positionsByAccount.containsKey(accountKey)) {
            return positionsByAccount.get(accountKey);
        }
        for (Map.Entry<String, List<RobinhoodAgenticPosition>> e : positionsByAccount.entrySet()) {
            if (accountEndsWith(e.getKey(), suffix)) {
                return e.getValue();
            }
        }
        return List.of();
    }

    private static String resolveFullAccountNumber(String accountKey, Set<String> fullNumbers) {
        if (fullNumbers.contains(accountKey)) {
            return accountKey;
        }
        for (String full : fullNumbers) {
            if (accountEndsWith(full, accountKey)) {
                return full;
            }
        }
        return accountKey;
    }

    private static String accountKind(boolean individual, boolean agentic, boolean managed) {
        if (individual) {
            return "INDIVIDUAL";
        }
        if (agentic) {
            return "AGENTIC";
        }
        if (managed) {
            return "MANAGED";
        }
        return "OTHER";
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
            String managedSuffix,
            List<RobinhoodAgenticPosition> allPositions) {
        List<String> list = new ArrayList<>(accountNumbers);
        list.sort(
                Comparator.<String>comparingInt(
                                acct -> accountSortRank(acct, individualSuffix, agenticSuffix, managedSuffix))
                        .thenComparing(acct -> suffixOf(acct, allPositions)));
        return list;
    }

    private static int accountSortRank(
            String accountNumber, String individualSuffix, String agenticSuffix, String managedSuffix) {
        if (accountEndsWith(accountNumber, individualSuffix)) {
            return 0;
        }
        if (accountEndsWith(accountNumber, agenticSuffix)) {
            return 1;
        }
        if (managedSuffix != null && accountEndsWith(accountNumber, managedSuffix)) {
            return 2;
        }
        return 3;
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
            boolean individual, boolean agentic, boolean managed, String suffix, String agenticNickname) {
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
        if (managed) {
            return "Managed " + maskSuffix(suffix);
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
                "Tracking window starts Apr 5, 2026 00:00 Central. Internal transfers (ITRF/Transfer to other RH accounts) are Out on the individual CSV and mirrored as In on Agentic, Managed, or matching •••• suffix. Starting balances default to $0 for new accounts; set per-suffix totals in robinhood_rh_account_starting_balance for gain/loss vs basis.");
        if (csvRowCount >= rowCap) {
            notes.add("Cash-flow query capped at " + rowCap + " rows; older transfers since cutoff may be omitted.");
        }
        if (connectionOpt.isEmpty() || connectionOpt.get().getLastSyncAt() == null) {
            notes.add("Connect Robinhood Agentic Trading for live holdings, options, and portfolio totals.");
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
        boolean changed = false;
        if (config.getRhAccountsTrackStartedAt() == null) {
            config.setRhAccountsTrackStartedAt(DEFAULT_TRACKING_START);
            changed = true;
        }
        if (config.getIndividualStartingTotalValue() == null) {
            config.setIndividualStartingTotalValue(BigDecimal.ZERO);
            changed = true;
        }
        if (config.getAgenticStartingTotalValue() == null) {
            config.setAgenticStartingTotalValue(BigDecimal.ZERO);
            changed = true;
        }
        if (config.getManagedAccountSuffix() == null || config.getManagedAccountSuffix().isBlank()) {
            config.setManagedAccountSuffix("4123");
            changed = true;
        }
        if (config.getManagedStartingTotalValue() == null) {
            config.setManagedStartingTotalValue(new BigDecimal("100.00"));
            changed = true;
        }
        if (changed) {
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
        config.setIndividualStartingTotalValue(BigDecimal.ZERO);
        config.setAgenticAccountSuffix("3550");
        config.setAgenticStartingTotalValue(BigDecimal.ZERO);
        config.setManagedAccountSuffix("4123");
        config.setManagedStartingTotalValue(new BigDecimal("100.00"));
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

    static record FlowTotals(
            BigDecimal deposits,
            BigDecimal withdrawals,
            BigDecimal internalIn,
            BigDecimal internalOut,
            BigDecimal net) {}

    private record HoldingsTotals(BigDecimal marketValue, BigDecimal costBasis, BigDecimal unrealizedPnL) {}

    private record PortfolioTotals(BigDecimal totalValue, BigDecimal cash, BigDecimal equityValue) {
        static PortfolioTotals empty() {
            return new PortfolioTotals(null, null, null);
        }
    }
}
