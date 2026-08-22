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

/**
 * Combines scheduled Robinhood daily closes into a personal net for the first-million roadmap.
 * Ammu's account is excluded.
 */
final class MarketsJourneyLiveNet {

    static final String AMMU_SUFFIX = "8696";
    static final String AUTO_NOTE_PREFIX = "Robinhood net as of";
    static final int MAX_DAILY_POINTS = 18;

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US);
    private static final DateTimeFormatter AS_OF_LABEL = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final List<String> ACCOUNT_ORDER = List.of("3370", "3550", "4123", "2835", "0440");

    record AccountSlice(String suffix, String label, BigDecimal value) {}

    record DayTotal(LocalDate date, BigDecimal total, List<AccountSlice> accounts) {}

    private MarketsJourneyLiveNet() {}

    static boolean isExcludedSuffix(String suffix) {
        return suffix != null && AMMU_SUFFIX.equals(suffix.trim());
    }

    static List<DayTotal> dailyTotals(List<RhScheduledTotalRow> rows) {
        Map<LocalDate, Map<String, BigDecimal>> byDate = new LinkedHashMap<>();
        if (rows != null) {
            for (RhScheduledTotalRow row : rows) {
                if (row == null || row.snapshotDate() == null || row.totalAccountValue() == null) {
                    continue;
                }
                String suffix = row.accountSuffix() == null ? "" : row.accountSuffix().trim();
                if (suffix.isEmpty() || isExcludedSuffix(suffix)) {
                    continue;
                }
                byDate.computeIfAbsent(row.snapshotDate(), d -> new LinkedHashMap<>())
                        .put(suffix, scale(row.totalAccountValue()));
            }
        }
        List<DayTotal> out = new ArrayList<>(byDate.size());
        for (Map.Entry<LocalDate, Map<String, BigDecimal>> e : byDate.entrySet()) {
            List<AccountSlice> accounts = slices(e.getValue());
            BigDecimal total = BigDecimal.ZERO;
            for (AccountSlice slice : accounts) {
                total = total.add(slice.value());
            }
            out.add(new DayTotal(e.getKey(), scale(total), accounts));
        }
        out.sort(Comparator.comparing(DayTotal::date));
        return out;
    }

    static List<DayTotal> sampleForChart(List<DayTotal> daily) {
        if (daily == null || daily.isEmpty()) {
            return List.of();
        }
        if (daily.size() <= MAX_DAILY_POINTS) {
            return List.copyOf(daily);
        }
        Map<String, DayTotal> monthEnd = new LinkedHashMap<>();
        for (DayTotal day : daily) {
            monthEnd.put(day.date().getYear() + "-" + day.date().getMonthValue(), day);
        }
        List<DayTotal> sampled = new ArrayList<>(monthEnd.values());
        DayTotal latest = daily.get(daily.size() - 1);
        if (sampled.isEmpty() || !sampled.get(sampled.size() - 1).date().equals(latest.date())) {
            sampled.add(latest);
        }
        return sampled;
    }

    static String periodLabel(DayTotal day, boolean latest) {
        if (latest && !isMonthEnd(day.date())) {
            return "As of " + day.date().format(AS_OF_LABEL);
        }
        return day.date().format(MONTH_LABEL);
    }

    static String actualNote(LocalDate date) {
        return AUTO_NOTE_PREFIX + " " + date + ". Excludes Ammu's a/c (\u2022\u2022\u2022\u20228696).";
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

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
