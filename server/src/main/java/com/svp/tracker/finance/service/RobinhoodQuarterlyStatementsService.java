package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.finance.domain.RobinhoodAccountCashIo;
import com.svp.tracker.finance.domain.RobinhoodRhDailySnapshot;
import com.svp.tracker.finance.domain.RobinhoodRhSupplementalCashFlow;
import com.svp.tracker.finance.dto.FinanceTaxDeskRhAccountRealizedDto;
import com.svp.tracker.finance.dto.RhScheduledTotalRow;
import com.svp.tracker.finance.dto.RobinhoodQuarterDto;
import com.svp.tracker.finance.dto.RobinhoodQuarterlyStatementsDto;
import com.svp.tracker.finance.dto.RobinhoodRhPeriodAccountColumnDto;
import com.svp.tracker.finance.dto.RobinhoodStatementDto;
import com.svp.tracker.finance.repository.RobinhoodAccountCashIoRepository;
import com.svp.tracker.finance.repository.RobinhoodRhDailySnapshotRepository;
import com.svp.tracker.finance.repository.RobinhoodRhSupplementalCashFlowRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Quarterly income, performance (P&amp;L) and cash flow statements for the tracked Robinhood accounts. Values come
 * from the 9 PM CT closes, money moved from the Cash I/O ledger, broker realized P&amp;L, and recorded interest.
 */
@Service
@RequiredArgsConstructor
public class RobinhoodQuarterlyStatementsService {

    private static final ZoneId CENTRAL = ZoneId.of("America/Chicago");
    private static final List<String> SUFFIX_ORDER = List.of("3370", "3550", "4123", "8696", "2835");
    private static final String INTEREST = "INTEREST";

    private final CurrentUserService currentUser;
    private final RobinhoodRhDailySnapshotRepository snapshotRepository;
    private final RobinhoodAccountTrackerConfigService accountTrackerConfigService;
    private final RobinhoodAccountCashIoRepository cashIoRepository;
    private final RobinhoodRhSupplementalCashFlowRepository supplementalRepository;
    private final RobinhoodBrokerRealizedPnlService realizedService;

    /** Broker YTD realized by account for a past date. Past dates never change, so they are kept. */
    private final Map<String, Map<String, BigDecimal>> realizedPast = new ConcurrentHashMap<>();

    public RobinhoodQuarterlyStatementsDto build(int year) {
        long owner = currentUser.requireUserId();
        LocalDate today = LocalDate.now(CENTRAL);

        Map<String, TreeMap<LocalDate, BigDecimal>> series = new HashMap<>();
        for (RhScheduledTotalRow row : snapshotRepository.findScheduledTotalsBetween(
                owner, LocalDate.of(year - 1, 12, 1), LocalDate.of(year, 12, 31))) {
            if (row.accountSuffix() == null || row.snapshotDate() == null || row.totalAccountValue() == null) {
                continue;
            }
            String suffix = row.accountSuffix().trim();
            if (!accountTrackerConfigService.isDailyTrackerSuffix(owner, suffix)) {
                continue;
            }
            series.computeIfAbsent(suffix, k -> new TreeMap<>()).put(row.snapshotDate(), row.totalAccountValue());
        }

        List<RobinhoodAccountCashIo> ledger = cashIoRepository.findByOwnerUserIdAndActivityDateBetweenOrderByActivityDateDescIdDesc(
                owner, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        Set<String> suffixSet = new LinkedHashSet<>();
        for (String s : SUFFIX_ORDER) {
            boolean hasLedger = ledger.stream().anyMatch(r -> s.equals(r.getAccountSuffix()));
            if ((series.containsKey(s) || hasLedger) && accountTrackerConfigService.isDailyTrackerSuffix(owner, s)) {
                suffixSet.add(s);
            }
        }
        List<String> suffixes = new ArrayList<>(suffixSet);

        Map<LocalDate, Map<String, BigDecimal>> cashByDate = new HashMap<>();
        List<RobinhoodQuarterDto> quarters = new ArrayList<>();
        for (int q = 1; q <= 4; q++) {
            LocalDate qs = LocalDate.of(year, (q - 1) * 3 + 1, 1);
            if (qs.isAfter(today)) {
                break;
            }
            LocalDate qe = qs.plusMonths(3).minusDays(1);
            LocalDate end = qe.isAfter(today) ? today : qe;
            List<RobinhoodStatementDto> accounts = new ArrayList<>();
            for (String suffix : suffixes) {
                accounts.add(accountStatement(owner, year, suffix, qs, end, series.get(suffix), ledger, cashByDate));
            }
            quarters.add(new RobinhoodQuarterDto(
                    year + "-Q" + q, "Q" + q + " " + year, qs, end, qe.isAfter(today), combine(accounts), accounts));
        }

        List<RobinhoodRhPeriodAccountColumnDto> columns = suffixes.stream()
                .map(s -> new RobinhoodRhPeriodAccountColumnDto(s, RobinhoodRhDailyTrackerAccountPolicy.displayLabel(s)))
                .toList();
        List<String> notes = List.of(
                "Opening and closing values are 9 PM CT closes. Where the tracker started after a quarter began, the "
                        + "statement runs from the first close it has.",
                "Deposits and withdrawals come from the Cash I/O ledger. Moves between your own Robinhood accounts are "
                        + "shown separately and cancel out in the combined view.",
                "Realized P&L is the broker figure for Individual, Agentic and Ammu's (not a tax document). Roth IRA and "
                        + "Managed show no realized figure.",
                "Income is interest recorded by hand. Dividends and fees are not tracked separately, so they sit inside "
                        + "the unrealized and other line.",
                "Cash moved to or from Predict is not in the ledger, so it shows up as trading cash.");
        return new RobinhoodQuarterlyStatementsDto(year, columns, quarters, notes);
    }

    private RobinhoodStatementDto accountStatement(
            long owner,
            int year,
            String suffix,
            LocalDate qs,
            LocalDate end,
            TreeMap<LocalDate, BigDecimal> series,
            List<RobinhoodAccountCashIo> ledger,
            Map<LocalDate, Map<String, BigDecimal>> cashByDate) {
        var open = series == null ? null : series.floorEntry(qs.minusDays(1));
        if (open == null && series != null) {
            var first = series.ceilingEntry(qs);
            open = first != null && !first.getKey().isAfter(end) ? first : null;
        }
        var close = series == null ? null : series.floorEntry(end);
        if (close != null && close.getKey().isBefore(qs)) {
            close = null;
        }
        if (open != null && close != null && close.getKey().isBefore(open.getKey())) {
            close = null;
        }
        LocalDate base = open != null ? open.getKey() : qs.minusDays(1);

        BigDecimal deposits = BigDecimal.ZERO;
        BigDecimal withdrawals = BigDecimal.ZERO;
        BigDecimal transfersIn = BigDecimal.ZERO;
        BigDecimal transfersOut = BigDecimal.ZERO;
        for (RobinhoodAccountCashIo row : ledger) {
            LocalDate day = row.getActivityDate();
            if (!suffix.equals(row.getAccountSuffix()) || day == null || !day.isAfter(base) || day.isAfter(end)) {
                continue;
            }
            BigDecimal amount = row.getAmount() == null ? BigDecimal.ZERO : row.getAmount().abs();
            boolean in = "IN".equalsIgnoreCase(row.getDirection() == null ? "" : row.getDirection().trim());
            boolean internal = isInternal(row.getCounterparty());
            if (in) {
                if (internal) {
                    transfersIn = transfersIn.add(amount);
                } else {
                    deposits = deposits.add(amount);
                }
            } else if (internal) {
                transfersOut = transfersOut.add(amount);
            } else {
                withdrawals = withdrawals.add(amount);
            }
        }

        BigDecimal income = BigDecimal.ZERO;
        for (RobinhoodRhSupplementalCashFlow flow :
                supplementalRepository
                        .findByOwnerUserIdAndAccountSuffixAndActivityDateBetweenAndFlowCategoryOrderByActivityDateAscIdAsc(
                                owner, suffix, base.plusDays(1), end, INTEREST)) {
            if ("IN".equalsIgnoreCase(flow.getDirection() == null ? "" : flow.getDirection().trim())) {
                income = income.add(flow.getAmount());
            }
        }

        BigDecimal realized = realizedBetween(owner, year, suffix, base, end);

        BigDecimal openingValue = open == null ? null : open.getValue();
        BigDecimal closingValue = close == null ? null : close.getValue();
        BigDecimal netIn = deposits.subtract(withdrawals).add(transfersIn).subtract(transfersOut);
        BigDecimal marketChange = null;
        BigDecimal unrealizedOther = null;
        BigDecimal returnPct = null;
        if (openingValue != null && closingValue != null) {
            marketChange = closingValue.subtract(openingValue).subtract(netIn);
            unrealizedOther = marketChange.subtract(income);
            if (realized != null) {
                unrealizedOther = unrealizedOther.subtract(realized);
            }
            BigDecimal basis = openingValue.add(netIn.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP));
            if (basis.signum() > 0) {
                returnPct = marketChange.multiply(BigDecimal.valueOf(100)).divide(basis, 2, RoundingMode.HALF_UP);
            }
        }

        BigDecimal openingCash = open == null ? null : cashOn(owner, open.getKey(), suffix, cashByDate);
        BigDecimal closingCash = close == null ? null : cashOn(owner, close.getKey(), suffix, cashByDate);
        BigDecimal tradingCash = null;
        if (openingCash != null && closingCash != null) {
            tradingCash = closingCash.subtract(openingCash).subtract(netIn).subtract(income);
        }
        return new RobinhoodStatementDto(
                suffix,
                RobinhoodRhDailyTrackerAccountPolicy.displayLabel(suffix),
                open == null ? null : open.getKey(),
                close == null ? null : close.getKey(),
                money(openingValue),
                money(closingValue),
                money(deposits),
                money(withdrawals),
                money(transfersIn),
                money(transfersOut),
                money(realized),
                money(income),
                money(marketChange),
                money(unrealizedOther),
                returnPct,
                money(openingCash),
                money(closingCash),
                money(tradingCash));
    }

    /** Sums the account statements. Moves between your own accounts stay visible but cancel in the net lines. */
    static RobinhoodStatementDto combine(List<RobinhoodStatementDto> rows) {
        List<RobinhoodStatementDto> live = rows.stream()
                .filter(r -> r.closingValue() != null || r.deposits().signum() != 0 || r.withdrawals().signum() != 0
                        || r.transfersIn().signum() != 0 || r.transfersOut().signum() != 0)
                .toList();
        boolean allOpen = live.stream().filter(r -> r.closingValue() != null).allMatch(r -> r.openingValue() != null);
        boolean hasClose = live.stream().anyMatch(r -> r.closingValue() != null);
        boolean allCash = live.stream().filter(r -> r.closingValue() != null).allMatch(r -> r.openingCash() != null && r.closingCash() != null);
        boolean allRealizedKnown = live.stream()
                .filter(r -> RobinhoodBrokerRealizedPnlService.TAX_DESK_SUFFIXES.contains(r.suffix()))
                .allMatch(r -> r.realized() != null);
        BigDecimal deposits = sum(live, RobinhoodStatementDto::deposits);
        BigDecimal withdrawals = sum(live, RobinhoodStatementDto::withdrawals);
        BigDecimal transfersIn = sum(live, RobinhoodStatementDto::transfersIn);
        BigDecimal transfersOut = sum(live, RobinhoodStatementDto::transfersOut);
        BigDecimal income = sum(live, RobinhoodStatementDto::income);
        BigDecimal realized = allRealizedKnown ? sum(live, r -> r.realized() == null ? BigDecimal.ZERO : r.realized()) : null;
        BigDecimal opening = hasClose && allOpen ? sum(live, r -> r.openingValue() == null ? BigDecimal.ZERO : r.openingValue()) : null;
        BigDecimal closing = hasClose ? sum(live, r -> r.closingValue() == null ? BigDecimal.ZERO : r.closingValue()) : null;
        BigDecimal netIn = deposits.subtract(withdrawals).add(transfersIn).subtract(transfersOut);
        BigDecimal market = opening != null && closing != null ? closing.subtract(opening).subtract(netIn) : null;
        BigDecimal unrealized = null;
        BigDecimal returnPct = null;
        if (market != null) {
            unrealized = market.subtract(income);
            if (realized != null) {
                unrealized = unrealized.subtract(realized);
            }
            BigDecimal basis = opening.add(netIn.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP));
            if (basis.signum() > 0) {
                returnPct = market.multiply(BigDecimal.valueOf(100)).divide(basis, 2, RoundingMode.HALF_UP);
            }
        }
        BigDecimal openingCash = hasClose && allCash ? sum(live, r -> r.openingCash() == null ? BigDecimal.ZERO : r.openingCash()) : null;
        BigDecimal closingCash = hasClose && allCash ? sum(live, r -> r.closingCash() == null ? BigDecimal.ZERO : r.closingCash()) : null;
        BigDecimal trading = openingCash != null && closingCash != null
                ? closingCash.subtract(openingCash).subtract(netIn).subtract(income)
                : null;
        LocalDate openingDate = live.stream().map(RobinhoodStatementDto::openingDate).filter(d -> d != null).min(LocalDate::compareTo).orElse(null);
        LocalDate closingDate = live.stream().map(RobinhoodStatementDto::closingDate).filter(d -> d != null).max(LocalDate::compareTo).orElse(null);
        return new RobinhoodStatementDto(
                "ALL", "All tracked accounts", openingDate, closingDate, money(opening), money(closing), money(deposits),
                money(withdrawals), money(transfersIn), money(transfersOut), money(realized), money(income), money(market),
                money(unrealized), returnPct, money(openingCash), money(closingCash), money(trading));
    }

    private static BigDecimal sum(List<RobinhoodStatementDto> rows, java.util.function.Function<RobinhoodStatementDto, BigDecimal> f) {
        BigDecimal total = BigDecimal.ZERO;
        for (RobinhoodStatementDto r : rows) {
            BigDecimal v = f.apply(r);
            if (v != null) {
                total = total.add(v);
            }
        }
        return total;
    }

    /** Broker realized P&amp;L between two dates for one account: YTD at the end minus YTD at the start. */
    private BigDecimal realizedBetween(long owner, int year, String suffix, LocalDate base, LocalDate end) {
        if (!RobinhoodBrokerRealizedPnlService.TAX_DESK_SUFFIXES.contains(suffix)) {
            return null;
        }
        Map<String, BigDecimal> atEnd = realizedYtd(owner, year, end);
        if (atEnd == null || !atEnd.containsKey(suffix)) {
            return null;
        }
        BigDecimal start = BigDecimal.ZERO;
        if (base.getYear() == year && !base.isBefore(LocalDate.of(year, 1, 1))) {
            Map<String, BigDecimal> atBase = realizedYtd(owner, year, base);
            if (atBase == null) {
                return null;
            }
            start = atBase.getOrDefault(suffix, BigDecimal.ZERO);
        }
        return atEnd.get(suffix).subtract(start);
    }

    private Map<String, BigDecimal> realizedYtd(long owner, int year, LocalDate asOf) {
        LocalDate today = LocalDate.now(CENTRAL);
        String key = owner + "|" + year + "|" + asOf;
        if (asOf.isBefore(today) && realizedPast.containsKey(key)) {
            return realizedPast.get(key);
        }
        Optional<RobinhoodBrokerRealizedPnlService.Fetched> stored = realizedService.storedOnOrBefore(owner, year, asOf);
        if (stored.isPresent() && !stored.get().asOf().isBefore(asOf.minusDays(1))) {
            return toMap(stored.get());
        }
        Optional<RobinhoodBrokerRealizedPnlService.Fetched> live = realizedService.fetchLive(owner, year, asOf);
        if (live.isEmpty()) {
            return null;
        }
        Map<String, BigDecimal> map = toMap(live.get());
        if (asOf.isBefore(today)) {
            realizedPast.put(key, map);
        }
        return map;
    }

    private static Map<String, BigDecimal> toMap(RobinhoodBrokerRealizedPnlService.Fetched fetched) {
        Map<String, BigDecimal> map = new HashMap<>();
        for (FinanceTaxDeskRhAccountRealizedDto a : fetched.accounts()) {
            map.put(a.suffix(), a.realized() == null ? BigDecimal.ZERO : a.realized());
        }
        return map;
    }

    private BigDecimal cashOn(long owner, LocalDate date, String suffix, Map<LocalDate, Map<String, BigDecimal>> cache) {
        Map<String, BigDecimal> bySuffix = cache.computeIfAbsent(date, d -> {
            Map<String, BigDecimal> m = new HashMap<>();
            for (RobinhoodRhDailySnapshot s : snapshotRepository.findByOwnerUserIdAndSnapshotDateAndCaptureKind(owner, d, "SCHEDULED")) {
                m.put(s.getAccountSuffix(), s.getCashBalance());
            }
            return m;
        });
        return bySuffix.get(suffix);
    }

    private static boolean isInternal(String counterparty) {
        if (counterparty == null) {
            return false;
        }
        String low = counterparty.toLowerCase(Locale.ROOT);
        return low.contains("robinhood account") || low.startsWith("roth ira");
    }

    private static BigDecimal money(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }
}
