package com.svp.tracker.management.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class ManagementDueCalendarSupport {

    private static final Pattern NOISE = Pattern.compile(
            "\\b(ACH|POS|DEBIT|CREDIT|CHECKCARD|PURCHASE|ONLINE|RECURRING|PAYMENT|PMT|WEB|MOBILE|CARD)\\b");
    private static final Pattern NON_ALNUM = Pattern.compile("[^A-Z0-9 ]+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private ManagementDueCalendarSupport() {}

    public static LocalDate occurrenceDate(int year, int month, int dayOfMonth) {
        YearMonth ym = YearMonth.of(year, month);
        int day = Math.min(Math.max(dayOfMonth, 1), ym.lengthOfMonth());
        return ym.atDay(day);
    }

    public static boolean appearsInMonth(
            boolean recurring, LocalDate startsOn, LocalDate oneOffDate, YearMonth month) {
        if (recurring) {
            if (startsOn == null) {
                return true;
            }
            return !YearMonth.from(startsOn).isAfter(month);
        }
        return oneOffDate != null && YearMonth.from(oneOffDate).equals(month);
    }

    public static String normalizePayee(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw.toUpperCase(Locale.US);
        s = NON_ALNUM.matcher(s).replaceAll(" ");
        s = NOISE.matcher(s).replaceAll(" ");
        return SPACES.matcher(s).replaceAll(" ").trim();
    }

    public static boolean payeeMatches(String counterparty, String description) {
        String left = normalizePayee(counterparty);
        String right = normalizePayee(description);
        if (left.length() < 3 || right.length() < 3) {
            return false;
        }
        return right.contains(left) || left.contains(right);
    }

    public static BigDecimal median(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<BigDecimal> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal a = sorted.get(n / 2 - 1);
        BigDecimal b = sorted.get(n / 2);
        return a.add(b).divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
    }

    public static int medianDay(List<Integer> days) {
        if (days == null || days.isEmpty()) {
            return 1;
        }
        List<Integer> sorted = new ArrayList<>(days);
        sorted.sort(Comparator.naturalOrder());
        return sorted.get((sorted.size() - 1) / 2);
    }
}
