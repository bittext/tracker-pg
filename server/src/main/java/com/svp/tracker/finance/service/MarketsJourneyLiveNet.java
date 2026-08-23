package com.svp.tracker.finance.service;

import com.svp.tracker.finance.domain.MarketsJourneyEntry;
import com.svp.tracker.finance.dto.RhScheduledTotalRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US);
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

    /** Last close of each month, plus the latest day when it is not already a month-end. */
    static List<DayTotal> monthStations(List<DayTotal> daily) {
        if (daily == null || daily.isEmpty()) {
            return List.of();
        }
        Map<String, DayTotal> monthEnd = new LinkedHashMap<>();
        for (DayTotal day : daily) {
            monthEnd.put(day.date().getYear() + "-" + day.date().getMonthValue(), day);
        }
        List<DayTotal> out = new ArrayList<>(monthEnd.values());
        DayTotal latest = daily.get(daily.size() - 1);
        if (out.isEmpty() || !out.get(out.size() - 1).date().equals(latest.date())) {
            out.add(latest);
        }
        return out;
    }

    static List<DayTotal> seriesForChart(List<DayTotal> daily) {
        return monthStations(daily);
    }

    static int indexOnOrBefore(List<DayTotal> daily, LocalDate asOf) {
        if (daily == null || daily.isEmpty()) {
            return -1;
        }
        if (asOf == null) {
            return daily.size() - 1;
        }
        for (int i = daily.size() - 1; i >= 0; i--) {
            if (!daily.get(i).date().isAfter(asOf)) {
                return i;
            }
        }
        return 0;
    }

    static String periodLabel(DayTotal day, boolean latest) {
        if (latest && !isMonthEnd(day.date())) {
            return "As of " + day.date().format(AS_OF_LABEL);
        }
        return day.date().format(MONTH_LABEL);
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

    static String accountType(String suffix, String accountKind) {
        return switch (suffix == null ? "" : suffix.trim()) {
            case "3370" -> "Margin";
            case "3550" -> "Limited margin";
            case "4123" -> "Managed";
            case "2835" -> "IRA";
            case "8696" -> "Brokerage";
            default -> accountKind == null || accountKind.isBlank() ? "Brokerage" : accountKind;
        };
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

    private static boolean isMonthEnd(LocalDate date) {
        return date.getDayOfMonth() == date.lengthOfMonth();
    }

    static BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
