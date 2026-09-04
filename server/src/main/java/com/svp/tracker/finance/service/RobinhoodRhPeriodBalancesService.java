package com.svp.tracker.finance.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.finance.dto.RhScheduledTotalRow;
import com.svp.tracker.finance.dto.RobinhoodRhPeriodAccountColumnDto;
import com.svp.tracker.finance.dto.RobinhoodRhPeriodAccountFigureDto;
import com.svp.tracker.finance.dto.RobinhoodRhPeriodBalanceRowDto;
import com.svp.tracker.finance.dto.RobinhoodRhPeriodBalancesDto;
import com.svp.tracker.finance.repository.RobinhoodRhDailySnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
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
 * close on or before period end (or latest close if the period is still open).
 */
@Service
@RequiredArgsConstructor
public class RobinhoodRhPeriodBalancesService {

    private static final ZoneId CENTRAL = ZoneId.of("America/Chicago");
    private static final List<String> PREFERRED_SUFFIX_ORDER =
            List.of("3370", "3550", "4123", "8696", "4190", "7581");

    private final CurrentUserService currentUser;
    private final RobinhoodRhDailySnapshotRepository snapshotRepository;
    private final RobinhoodAccountTrackerConfigService accountTrackerConfigService;

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

        List<String> suffixes = orderSuffixes(suffixesInYear);
        List<RobinhoodRhPeriodAccountColumnDto> accounts = suffixes.stream()
                .map(s -> new RobinhoodRhPeriodAccountColumnDto(
                        s, RobinhoodRhDailyTrackerAccountPolicy.displayLabel(s)))
                .toList();

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
                    seriesBySuffix));
        }

        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = YearMonth.of(year, 12).isAfter(currentYm) ? today : LocalDate.of(year, 12, 31);
        RobinhoodRhPeriodBalanceRowDto yearBalance = buildRow(
                String.valueOf(year),
                "Year " + year,
                yearStart,
                yearEnd,
                year == today.getYear(),
                suffixes,
                seriesBySuffix);

        String note = suffixes.isEmpty()
                ? "No Daily Tracker scheduled closes in " + year + " yet."
                : "Opening is the last 9 PM CT close before the period (calendar midnight start). "
                        + "If tracking started later, opening is the first close in that period. "
                        + "Closing is the last 9 PM CT close on or before the period end.";
        return new RobinhoodRhPeriodBalancesDto(year, note, accounts, months, yearBalance);
    }

    private static RobinhoodRhPeriodBalanceRowDto buildRow(
            String key,
            String label,
            LocalDate periodStart,
            LocalDate periodEnd,
            boolean currentPeriod,
            List<String> suffixes,
            Map<String, TreeMap<LocalDate, BigDecimal>> seriesBySuffix) {
        List<RobinhoodRhPeriodAccountFigureDto> figures = new ArrayList<>();
        BigDecimal combinedStart = BigDecimal.ZERO;
        BigDecimal combinedEnd = BigDecimal.ZERO;
        boolean anyStart = false;
        boolean anyEnd = false;
        for (String suffix : suffixes) {
            NavigableMap<LocalDate, BigDecimal> series =
                    seriesBySuffix.getOrDefault(suffix, new TreeMap<>());
            ClosePoint start = openingForPeriod(series, periodStart, periodEnd);
            ClosePoint end = lastOnOrBefore(series, periodEnd);
            if (start != null) {
                combinedStart = combinedStart.add(start.value());
                anyStart = true;
            }
            if (end != null) {
                combinedEnd = combinedEnd.add(end.value());
                anyEnd = true;
            }
            figures.add(new RobinhoodRhPeriodAccountFigureDto(
                    suffix,
                    start == null ? null : scaleMoney(start.value()),
                    end == null ? null : scaleMoney(end.value()),
                    start == null || end == null ? null : scaleMoney(end.value().subtract(start.value())),
                    start == null ? null : start.date(),
                    end == null ? null : end.date()));
        }
        return new RobinhoodRhPeriodBalanceRowDto(
                key,
                label,
                periodStart,
                periodEnd,
                currentPeriod,
                anyStart ? scaleMoney(combinedStart) : null,
                anyEnd ? scaleMoney(combinedEnd) : null,
                anyStart && anyEnd ? scaleMoney(combinedEnd.subtract(combinedStart)) : null,
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
}
