package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.finance.dto.RhScheduledTotalRow;
import com.svp.tracker.finance.dto.RobinhoodRhPeriodAccountColumnDto;
import com.svp.tracker.finance.dto.RobinhoodRhPeriodAccountFigureDto;
import com.svp.tracker.finance.dto.RobinhoodRhPeriodBalanceRowDto;
import com.svp.tracker.finance.dto.RobinhoodRhPeriodBalancesDto;
import com.svp.tracker.finance.domain.RobinhoodAccountCashIo;
import com.svp.tracker.finance.repository.RobinhoodAccountCashIoRepository;
import com.svp.tracker.finance.repository.RobinhoodRhDailySnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Month and year opening/closing balances from Daily Tracker 9 PM CT scheduled closes.
 * Opening = last close before the calendar period (midnight start). If none exists (tracker
 * started mid-period), opening is the first close on or after period start. Closing = last
 * close on or before period end (or latest close if the period is still open). Cash added /
 * taken out comes from the Cash I/O ledger in that window; market change is book change minus
 * deposits plus withdrawals.
 */
@Service
@RequiredArgsConstructor
public class RobinhoodRhPeriodBalancesService {

    private static final ZoneId CENTRAL = ZoneId.of("America/Chicago");
    private static final DateTimeFormatter SHORT_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US);
    private static final List<String> PREFERRED_SUFFIX_ORDER =
            List.of("3370", "3550", "4123", "8696", "4190", "7581");

    private final CurrentUserService currentUser;
    private final RobinhoodRhDailySnapshotRepository snapshotRepository;
    private final RobinhoodAccountTrackerConfigService accountTrackerConfigService;
    private final RobinhoodAccountCashIoRepository cashIoRepository;

    @Transactional(readOnly = true)
    public RobinhoodRhPeriodBalancesDto build(int year) {
        long ownerUserId = currentUser.requireUserId();
        LocalDate today = LocalDate.now(CENTRAL);
        LocalDate from = LocalDate.of(year - 1, 12, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        if (to.isAfter(today)) {
            to = today;
        }

        Map<String, TreeMap<LocalDate, BigDecimal>> seriesBySuffix = new LinkedHashMap<>();
        Set<String> suffixesInYear = new LinkedHashSet<>();
        for (RhScheduledTotalRow row :
                snapshotRepository.findScheduledTotalsBetween(ownerUserId, from, to)) {
            if (row.accountSuffix() == null
                    || row.accountSuffix().isBlank()
                    || !accountTrackerConfigService.isDailyTrackerSuffix(ownerUserId, row.accountSuffix())) {
                continue;
            }
            String suffix = row.accountSuffix().trim();
            seriesBySuffix
                    .computeIfAbsent(suffix, k -> new TreeMap<>())
                    .put(row.snapshotDate(), nullToZero(row.totalAccountValue()));
            if (row.snapshotDate() != null && row.snapshotDate().getYear() == year) {
                suffixesInYear.add(suffix);
            }
        }

        Map<String, ClosePoint> liveEnds = new LinkedHashMap<>();
        for (RhScheduledTotalRow row : snapshotRepository.findLatestTotalsBySuffix(ownerUserId)) {
            if (row.accountSuffix() == null
                    || row.accountSuffix().isBlank()
                    || !accountTrackerConfigService.isDailyTrackerSuffix(ownerUserId, row.accountSuffix())) {
                continue;
            }
            String suffix = row.accountSuffix().trim();
            liveEnds.put(suffix, new ClosePoint(row.snapshotDate(), nullToZero(row.totalAccountValue())));
            suffixesInYear.add(suffix);
        }

        List<String> suffixes = orderSuffixes(suffixesInYear);
        List<RobinhoodRhPeriodAccountColumnDto> accounts = suffixes.stream()
                .map(s -> new RobinhoodRhPeriodAccountColumnDto(
                        s, RobinhoodRhDailyTrackerAccountPolicy.displayLabel(s)))
                .toList();

        Map<String, List<RobinhoodAccountCashIo>> cashBySuffix = loadCashIo(ownerUserId, yearStartForCash(year), to);

        List<RobinhoodRhPeriodBalanceRowDto> months = new ArrayList<>();
        YearMonth currentYm = YearMonth.from(today);
        for (int month = 1; month <= 12; month++) {
            YearMonth ym = YearMonth.of(year, month);
            if (ym.isAfter(currentYm)) {
                break;
            }
            LocalDate periodStart = ym.atDay(1);
            LocalDate periodEnd = ym.equals(currentYm) ? today : ym.atEndOfMonth();
            months.add(buildRow(
                    String.format("%04d-%02d", year, month),
                    ym.getMonth().getDisplayName(TextStyle.FULL, Locale.US) + " " + year,
                    periodStart,
                    periodEnd,
                    ym.equals(currentYm),
                    suffixes,
                    seriesBySuffix,
                    Map.of(),
                    cashBySuffix));
        }

        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = YearMonth.of(year, 12).isAfter(currentYm) ? today : LocalDate.of(year, 12, 31);
        boolean yearOpen = year == today.getYear();
        RobinhoodRhPeriodBalanceRowDto yearBalance = buildRow(
                String.valueOf(year),
                "Year " + year,
                yearStart,
                yearEnd,
                yearOpen,
                suffixes,
                seriesBySuffix,
                yearOpen ? liveEnds : Map.of(),
                cashBySuffix);

        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate thisMonthStart = currentYm.atDay(1);
        LocalDate ytdStart = LocalDate.of(today.getYear(), 1, 1);
        List<RobinhoodRhPeriodBalanceRowDto> windows = List.of(
                buildRow(
                        "day",
                        "Today",
                        today,
                        today,
                        true,
                        suffixes,
                        seriesBySuffix,
                        liveEnds,
                        cashBySuffix),
                buildRow(
                        "week",
                        "This week · " + SHORT_DAY.format(weekStart) + "–" + SHORT_DAY.format(today),
                        weekStart,
                        today,
                        true,
                        suffixes,
                        seriesBySuffix,
                        liveEnds,
                        cashBySuffix),
                buildRow(
                        "month",
                        currentYm.getMonth().getDisplayName(TextStyle.FULL, Locale.US) + " so far",
                        thisMonthStart,
                        today,
                        true,
                        suffixes,
                        seriesBySuffix,
                        liveEnds,
                        cashBySuffix),
                buildRow(
                        "ytd",
                        "Year to date",
                        ytdStart,
                        today,
                        true,
                        suffixes,
                        seriesBySuffix,
                        liveEnds,
                        cashBySuffix),
                yearBalance);

        String note = suffixes.isEmpty()
                ? "No Daily Tracker scheduled closes in " + year + " yet."
                : "Opening is the last 9 PM CT close before the period (calendar midnight start). "
                        + "If tracking started later, opening is the first close in that period. "
                        + "Day / week / month / YTD end on the latest hourly capture. "
                        + "Month rows still use the official 9 PM CT close. "
                        + "Added / taken out is Cash I/O in the same window; after cash is the value change with deposits and withdrawals removed.";
        return new RobinhoodRhPeriodBalancesDto(year, note, accounts, windows, months, yearBalance);
    }

    private static RobinhoodRhPeriodBalanceRowDto buildRow(
            String key,
            String label,
            LocalDate periodStart,
            LocalDate periodEnd,
            boolean currentPeriod,
            List<String> suffixes,
            Map<String, TreeMap<LocalDate, BigDecimal>> seriesBySuffix,
            Map<String, ClosePoint> liveEnds,
            Map<String, List<RobinhoodAccountCashIo>> cashBySuffix) {
        List<RobinhoodRhPeriodAccountFigureDto> figures = new ArrayList<>();
        BigDecimal combinedStart = BigDecimal.ZERO;
        BigDecimal combinedEnd = BigDecimal.ZERO;
        BigDecimal combinedAdded = BigDecimal.ZERO;
        BigDecimal combinedRemoved = BigDecimal.ZERO;
        boolean anyStart = false;
        boolean anyEnd = false;
        for (String suffix : suffixes) {
            NavigableMap<LocalDate, BigDecimal> series =
                    seriesBySuffix.getOrDefault(suffix, new TreeMap<>());
            ClosePoint start = openingForPeriod(series, periodStart, periodEnd);
            ClosePoint end = lastOnOrBefore(series, periodEnd);
            ClosePoint live = liveEnds.get(suffix);
            if (currentPeriod && live != null && (end == null || !live.date().isBefore(end.date()))) {
                end = live;
            }
            if (start != null) {
                combinedStart = combinedStart.add(start.value());
                anyStart = true;
            }
            if (end != null) {
                combinedEnd = combinedEnd.add(end.value());
                anyEnd = true;
            }
            CashTotals cash = cashTotals(cashBySuffix.get(suffix), periodStart, periodEnd);
            combinedAdded = combinedAdded.add(cash.added());
            combinedRemoved = combinedRemoved.add(cash.removed());
            BigDecimal bookChange = start == null || end == null ? null : end.value().subtract(start.value());
            figures.add(new RobinhoodRhPeriodAccountFigureDto(
                    suffix,
                    start == null ? null : scaleMoney(start.value()),
                    end == null ? null : scaleMoney(end.value()),
                    bookChange == null ? null : scaleMoney(bookChange),
                    start == null ? null : start.date(),
                    end == null ? null : end.date(),
                    scaleMoney(cash.added()),
                    scaleMoney(cash.removed()),
                    bookChange == null ? null : scaleMoney(marketChange(bookChange, cash))));
        }
        BigDecimal bookCombined = anyStart && anyEnd ? combinedEnd.subtract(combinedStart) : null;
        CashTotals combinedCash = new CashTotals(combinedAdded, combinedRemoved);
        return new RobinhoodRhPeriodBalanceRowDto(
                key,
                label,
                periodStart,
                periodEnd,
                currentPeriod,
                anyStart ? scaleMoney(combinedStart) : null,
                anyEnd ? scaleMoney(combinedEnd) : null,
                bookCombined == null ? null : scaleMoney(bookCombined),
                scaleMoney(combinedAdded),
                scaleMoney(combinedRemoved),
                bookCombined == null ? null : scaleMoney(marketChange(bookCombined, combinedCash)),
                figures);
    }

    static ClosePoint lastBefore(NavigableMap<LocalDate, BigDecimal> series, LocalDate exclusive) {
        if (series == null || series.isEmpty() || exclusive == null) {
            return null;
        }
        var entry = series.lowerEntry(exclusive);
        return entry == null ? null : new ClosePoint(entry.getKey(), entry.getValue());
    }

    static ClosePoint firstOnOrAfter(NavigableMap<LocalDate, BigDecimal> series, LocalDate inclusive) {
        if (series == null || series.isEmpty() || inclusive == null) {
            return null;
        }
        var entry = series.ceilingEntry(inclusive);
        return entry == null ? null : new ClosePoint(entry.getKey(), entry.getValue());
    }

    /**
     * Prefer the last close before midnight start. If the series begins inside the period
     * (no prior close), use the first close on or after period start that is still in-range.
     */
    static ClosePoint openingForPeriod(
            NavigableMap<LocalDate, BigDecimal> series, LocalDate periodStart, LocalDate periodEnd) {
        ClosePoint prior = lastBefore(series, periodStart);
        if (prior != null) {
            return prior;
        }
        ClosePoint firstInPeriod = firstOnOrAfter(series, periodStart);
        if (firstInPeriod != null && periodEnd != null && !firstInPeriod.date().isAfter(periodEnd)) {
            return firstInPeriod;
        }
        return null;
    }

    static ClosePoint lastOnOrBefore(NavigableMap<LocalDate, BigDecimal> series, LocalDate inclusive) {
        if (series == null || series.isEmpty() || inclusive == null) {
            return null;
        }
        var entry = series.floorEntry(inclusive);
        return entry == null ? null : new ClosePoint(entry.getKey(), entry.getValue());
    }

    private Map<String, List<RobinhoodAccountCashIo>> loadCashIo(long ownerUserId, LocalDate from, LocalDate to) {
        Map<String, List<RobinhoodAccountCashIo>> out = new LinkedHashMap<>();
        for (RobinhoodAccountCashIo row :
                cashIoRepository.findByOwnerUserIdAndActivityDateBetweenOrderByActivityDateDescIdDesc(
                        ownerUserId, from, to)) {
            if (row.getAccountSuffix() == null || row.getAccountSuffix().isBlank()) {
                continue;
            }
            out.computeIfAbsent(row.getAccountSuffix().trim(), k -> new ArrayList<>()).add(row);
        }
        return out;
    }

    private static LocalDate yearStartForCash(int year) {
        return LocalDate.of(year, 1, 1);
    }

    static CashTotals cashTotals(List<RobinhoodAccountCashIo> rows, LocalDate periodStart, LocalDate periodEnd) {
        BigDecimal added = BigDecimal.ZERO;
        BigDecimal removed = BigDecimal.ZERO;
        if (rows == null || rows.isEmpty() || periodStart == null || periodEnd == null) {
            return new CashTotals(added, removed);
        }
        for (RobinhoodAccountCashIo row : rows) {
            LocalDate day = row.getActivityDate();
            if (day == null || day.isBefore(periodStart) || day.isAfter(periodEnd)) {
                continue;
            }
            BigDecimal amount = row.getAmount() == null ? BigDecimal.ZERO : row.getAmount().abs();
            String dir = row.getDirection() == null ? "" : row.getDirection().trim().toUpperCase(Locale.ROOT);
            if ("IN".equals(dir)) {
                added = added.add(amount);
            } else if ("OUT".equals(dir)) {
                removed = removed.add(amount);
            }
        }
        return new CashTotals(added, removed);
    }

    static BigDecimal marketChange(BigDecimal bookChange, CashTotals cash) {
        if (bookChange == null) {
            return null;
        }
        CashTotals flow = cash == null ? CashTotals.ZERO : cash;
        return bookChange.subtract(flow.added()).add(flow.removed());
    }

    private static List<String> orderSuffixes(Set<String> suffixes) {
        List<String> out = new ArrayList<>();
        for (String preferred : PREFERRED_SUFFIX_ORDER) {
            if (suffixes.contains(preferred)) {
                out.add(preferred);
            }
        }
        suffixes.stream().filter(s -> !out.contains(s)).sorted(Comparator.naturalOrder()).forEach(out::add);
        return out;
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

    record ClosePoint(LocalDate date, BigDecimal value) {}

    record CashTotals(BigDecimal added, BigDecimal removed) {
        static final CashTotals ZERO = new CashTotals(BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
