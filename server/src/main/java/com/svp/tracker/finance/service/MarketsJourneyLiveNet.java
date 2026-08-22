package com.svp.tracker.finance.service;

import com.svp.tracker.finance.domain.MarketsJourneyEntry;
import com.svp.tracker.finance.dto.RhScheduledTotalRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Combines scheduled Robinhood daily closes into a personal net for the first-million roadmap.
 * Every Daily Tracker account is included, including Ammu's. Missing accounts on a day keep
 * their last known close.
 */
final class MarketsJourneyLiveNet {

    static final String AUTO_NOTE_PREFIX = "Robinhood net as of";
    static final int RECENT_DAILY_DAYS = 90;

    private static final DateTimeFormatter AS_OF_LABEL = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final List<String> ACCOUNT_ORDER = List.of("3370", "3550", "4123", "8696", "2835", "0440");

    record AccountSlice(String suffix, String label, BigDecimal value) {}

    record DayTotal(LocalDate date, BigDecimal total, List<AccountSlice> accounts) {}

    private MarketsJourneyLiveNet() {}

    static List<DayTotal> dailyTotals(List<RhScheduledTotalRow> rows) {
        Map<LocalDate, Map<String, BigDecimal>> observed = new LinkedHashMap<>();
        if (rows != null) {
            for (RhScheduledTotalRow row : rows) {
                if (row == null || row.snapshotDate() == null || row.totalAccountValue() == null) {
                    continue;
                }
                String suffix = row.accountSuffix() == null ? "" : row.accountSuffix().trim();
                if (suffix.isEmpty()) {
                    continue;
                }
                observed
                        .computeIfAbsent(row.snapshotDate(), d -> new LinkedHashMap<>())
                        .put(suffix, scale(row.totalAccountValue()));
            }
        }
        List<LocalDate> dates = new ArrayList<>(new TreeSet<>(observed.keySet()));
        Map<String, BigDecimal> lastKnown = new LinkedHashMap<>();
        List<DayTotal> out = new ArrayList<>(dates.size());
        for (LocalDate date : dates) {
            lastKnown.putAll(observed.get(date));
            List<AccountSlice> accounts = slices(lastKnown);
            BigDecimal total = BigDecimal.ZERO;
            for (AccountSlice slice : accounts) {
                total = total.add(slice.value());
            }
            out.add(new DayTotal(date, scale(total), accounts));
        }
        return out;
    }

    /** Month-ends until the last {@value #RECENT_DAILY_DAYS} days, then every close — for the graph. */
    static List<DayTotal> seriesForChart(List<DayTotal> daily) {
        if (daily == null || daily.isEmpty()) {
            return List.of();
        }
        if (daily.size() <= RECENT_DAILY_DAYS) {
            return List.copyOf(daily);
        }
        LocalDate cutoff = daily.get(daily.size() - 1).date().minusDays(RECENT_DAILY_DAYS);
        Map<String, DayTotal> monthEnd = new LinkedHashMap<>();
        List<DayTotal> recent = new ArrayList<>();
        for (DayTotal day : daily) {
            if (!day.date().isBefore(cutoff)) {
                recent.add(day);
            } else {
                monthEnd.put(day.date().getYear() + "-" + day.date().getMonthValue(), day);
            }
        }
        List<DayTotal> out = new ArrayList<>(monthEnd.values());
        out.addAll(recent);
        out.sort(Comparator.comparing(DayTotal::date));
        return out;
    }

    static String periodLabel(DayTotal day) {
        return "As of " + day.date().format(AS_OF_LABEL);
    }

    static String actualNote(LocalDate date) {
        return AUTO_NOTE_PREFIX
                + " "
                + date
                + ". All Robinhood Daily Tracker accounts, including Ammu's a/c.";
    }

    static boolean isAutoManaged(MarketsJourneyEntry row) {
        if (row == null) {
            return false;
        }
        String note = row.getActualNote() == null ? "" : row.getActualNote().trim();
        if (note.startsWith(AUTO_NOTE_PREFIX)) {
            return true;
        }
        boolean emptyNotes = note.isEmpty() && (row.getTargetNote() == null || row.getTargetNote().isBlank());
        return row.getActualAmount() == null && row.getTargetAmount() == null && emptyNotes;
    }

    static String accountLabel(String suffix) {
        if ("2835".equals(suffix)) {
            return "Roth IRA (...2835)";
        }
        return RobinhoodRhDailyTrackerAccountPolicy.displayLabel(suffix);
    }

    static BigDecimal dayChange(DayTotal prior, DayTotal current) {
        if (prior == null || current == null) {
            return null;
        }
        return scale(current.total().subtract(prior.total()));
    }

    static BigDecimal dayChangePct(DayTotal prior, DayTotal current) {
        if (prior == null || current == null || prior.total().signum() == 0) {
            return null;
        }
        return current.total()
                .subtract(prior.total())
                .multiply(BigDecimal.valueOf(100))
                .divide(prior.total(), 2, RoundingMode.HALF_UP);
    }

    static BigDecimal accountDayChange(DayTotal prior, String suffix, BigDecimal currentValue) {
        if (prior == null || suffix == null || currentValue == null) {
            return null;
        }
        for (AccountSlice slice : prior.accounts()) {
            if (suffix.equals(slice.suffix())) {
                return scale(currentValue.subtract(slice.value()));
            }
        }
        return null;
    }

    private static List<AccountSlice> slices(Map<String, BigDecimal> bySuffix) {
        List<AccountSlice> out = new ArrayList<>(bySuffix.size());
        Set<String> seen = new java.util.HashSet<>();
        for (String preferred : ACCOUNT_ORDER) {
            BigDecimal value = bySuffix.get(preferred);
            if (value != null) {
                out.add(new AccountSlice(preferred, accountLabel(preferred), value));
                seen.add(preferred);
            }
        }
        List<String> rest = new ArrayList<>();
        for (String suffix : bySuffix.keySet()) {
            if (!seen.contains(suffix)) {
                rest.add(suffix);
            }
        }
        rest.sort(String::compareTo);
        for (String suffix : rest) {
            out.add(new AccountSlice(suffix, accountLabel(suffix), bySuffix.get(suffix)));
        }
        return out;
    }

    static BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
