package com.svp.tracker.management.service;

import com.svp.tracker.management.domain.ManagementDueSide;
import com.svp.tracker.management.dto.ManagementDueSuggestionDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Builds Due suggestions from Banking history: monthly bills and other large debits. */
public final class ManagementDueSuggestionSupport {

    public static final String KIND_MONTHLY = "MONTHLY";
    public static final String KIND_BIG_DEBIT = "BIG_DEBIT";

    static final int MONTHLY_LIMIT = 12;
    static final int BIG_DEBIT_LIMIT = 10;
    static final BigDecimal MIN_BIG_DEBIT = new BigDecimal("250.00");
    static final BigDecimal ALWAYS_BIG_DEBIT = new BigDecimal("1000.00");

    private static final DateTimeFormatter LAST_SEEN = DateTimeFormatter.ofPattern("MMM d", Locale.US);

    private ManagementDueSuggestionSupport() {}

    public record Txn(long id, LocalDate date, BigDecimal amount, String description) {}

    public record Tracked(ManagementDueSide side, String counterparty) {}

    public static List<ManagementDueSuggestionDto> build(
            List<Txn> rows, Set<Long> internalIds, List<Tracked> tracked, YearMonth focus) {
        Map<String, Acc> acc = new LinkedHashMap<>();
        List<BigDecimal> debitAbs = new ArrayList<>();
        if (rows != null) {
            for (Txn row : rows) {
                if (row == null || row.amount() == null || row.amount().signum() == 0) {
                    continue;
                }
                if (internalIds != null && internalIds.contains(row.id())) {
                    continue;
                }
                if (row.date() == null) {
                    continue;
                }
                String key = ManagementDueCalendarSupport.normalizePayee(row.description());
                if (key.length() < 4) {
                    continue;
                }
                ManagementDueSide side =
                        row.amount().signum() < 0 ? ManagementDueSide.PAYABLE : ManagementDueSide.RECEIVABLE;
                Acc bucket = acc.computeIfAbsent(side.name() + "|" + key, ignored -> new Acc(side));
                bucket.counterparty = row.description().trim();
                bucket.amounts.add(row.amount().abs());
                bucket.days.add(row.date().getDayOfMonth());
                bucket.months.add(YearMonth.from(row.date()));
                bucket.dates.add(row.date());
                if (side == ManagementDueSide.PAYABLE) {
                    debitAbs.add(row.amount().abs());
                }
            }
        }

        BigDecimal floor = bigDebitFloor(debitAbs);
        List<ManagementDueSuggestionDto> monthly = new ArrayList<>();
        List<ManagementDueSuggestionDto> bigDebits = new ArrayList<>();
        for (Acc bucket : acc.values()) {
            if (alreadyTracked(tracked, bucket.side, bucket.counterparty)) {
                continue;
            }
            boolean monthlyCadence = isMonthly(bucket, focus);
            if (monthlyCadence) {
                monthly.add(toSuggestion(
                        bucket,
                        focus,
                        KIND_MONTHLY,
                        ManagementDueCalendarSupport.median(bucket.amounts),
                        monthlyDetail(bucket, focus)));
                continue;
            }
            if (bucket.side == ManagementDueSide.PAYABLE && isBigDebit(bucket, focus, floor)) {
                bigDebits.add(toSuggestion(
                        bucket, focus, KIND_BIG_DEBIT, largestAmount(bucket), bigDebitDetail(bucket)));
            }
        }

        monthly.sort(Comparator.comparingInt(ManagementDueSuggestionDto::sampleCount)
                .reversed()
                .thenComparing(ManagementDueSuggestionDto::estimatedAmount, Comparator.nullsLast(Comparator.reverseOrder())));
        bigDebits.sort(Comparator.comparing(ManagementDueSuggestionDto::estimatedAmount, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparingInt(ManagementDueSuggestionDto::sampleCount)
                .reversed());

        List<ManagementDueSuggestionDto> out = new ArrayList<>();
        out.addAll(limit(monthly, MONTHLY_LIMIT));
        out.addAll(limit(bigDebits, BIG_DEBIT_LIMIT));
        return List.copyOf(out);
    }

    static boolean isMonthly(Acc bucket, YearMonth focus) {
        int distinctMonths = distinctMonthCount(bucket);
        if (distinctMonths < 2) {
            return false;
        }
        return hitsFocusMonth(bucket, focus) || distinctMonths >= 3;
    }

    static boolean isBigDebit(Acc bucket, YearMonth focus, BigDecimal floor) {
        BigDecimal representative = largestAmount(bucket);
        if (representative == null || floor == null) {
            return false;
        }
        if (representative.compareTo(ALWAYS_BIG_DEBIT) >= 0) {
            return true;
        }
        if (representative.compareTo(floor) < 0) {
            return false;
        }
        return hitsFocusMonth(bucket, focus);
    }

    static BigDecimal bigDebitFloor(List<BigDecimal> debitAbs) {
        if (debitAbs == null || debitAbs.isEmpty()) {
            return MIN_BIG_DEBIT;
        }
        List<BigDecimal> sorted = new ArrayList<>(debitAbs);
        sorted.sort(Comparator.naturalOrder());
        BigDecimal p75 = sorted.get(Math.min(sorted.size() - 1, (sorted.size() * 3) / 4));
        if (p75.compareTo(MIN_BIG_DEBIT) < 0) {
            return MIN_BIG_DEBIT;
        }
        return p75.setScale(2, RoundingMode.HALF_UP);
    }

    private static ManagementDueSuggestionDto toSuggestion(
            Acc bucket, YearMonth focus, String kind, BigDecimal amount, String detail) {
        return new ManagementDueSuggestionDto(
                bucket.side.name(),
                shorten(bucket.counterparty),
                typicalDay(bucket, focus),
                amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP),
                bucket.amounts.size(),
                kind,
                detail);
    }

    private static BigDecimal largestAmount(Acc bucket) {
        return bucket.amounts.stream().max(Comparator.naturalOrder()).orElse(null);
    }

    private static int typicalDay(Acc bucket, YearMonth focus) {
        List<Integer> focusDays = new ArrayList<>();
        for (LocalDate date : bucket.dates) {
            if (focus != null && date.getMonth() == focus.getMonth()) {
                focusDays.add(date.getDayOfMonth());
            }
        }
        return ManagementDueCalendarSupport.medianDay(focusDays.isEmpty() ? bucket.days : focusDays);
    }

    private static String monthlyDetail(Acc bucket, YearMonth focus) {
        String monthBit = hitsFocusMonth(bucket, focus) ? "Usually this month" : "Monthly";
        return monthBit + " · " + bucket.amounts.size() + (bucket.amounts.size() == 1 ? " time" : " times");
    }

    private static String bigDebitDetail(Acc bucket) {
        LocalDate last = bucket.dates.stream().max(Comparator.naturalOrder()).orElse(null);
        if (last == null) {
            return "Large debit";
        }
        return "Large debit · last " + LAST_SEEN.format(last);
    }

    private static boolean hitsFocusMonth(Acc bucket, YearMonth focus) {
        if (focus == null) {
            return true;
        }
        for (YearMonth month : bucket.months) {
            if (month.getMonth() == focus.getMonth()) {
                return true;
            }
        }
        return false;
    }

    private static int distinctMonthCount(Acc bucket) {
        return (int) bucket.months.stream().distinct().count();
    }

    private static boolean alreadyTracked(List<Tracked> tracked, ManagementDueSide side, String counterparty) {
        if (tracked == null) {
            return false;
        }
        for (Tracked item : tracked) {
            if (item.side() == side
                    && ManagementDueCalendarSupport.payeeMatches(item.counterparty(), counterparty)) {
                return true;
            }
        }
        return false;
    }

    private static List<ManagementDueSuggestionDto> limit(List<ManagementDueSuggestionDto> rows, int max) {
        if (rows.size() <= max) {
            return rows;
        }
        return rows.subList(0, max);
    }

    private static String shorten(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        return trimmed.length() > 48 ? trimmed.substring(0, 48).trim() : trimmed;
    }

    static final class Acc {
        final ManagementDueSide side;
        String counterparty = "";
        final List<BigDecimal> amounts = new ArrayList<>();
        final List<Integer> days = new ArrayList<>();
        final List<YearMonth> months = new ArrayList<>();
        final List<LocalDate> dates = new ArrayList<>();

        Acc(ManagementDueSide side) {
            this.side = side;
        }
    }
}
