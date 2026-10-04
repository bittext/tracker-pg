package com.svp.tracker.management.service;

import com.svp.tracker.management.dto.ManagementDueReportDto;
import com.svp.tracker.management.dto.ManagementDueReportDto.Day;
import com.svp.tracker.management.dto.ManagementDueReportDto.MonthBar;
import com.svp.tracker.management.dto.ManagementDueReportDto.Period;
import com.svp.tracker.management.dto.ManagementDueReportDto.Row;
import com.svp.tracker.management.dto.ManagementDueReportDto.Who;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class ManagementDueReportSupport {

    private ManagementDueReportSupport() {}

    public static ManagementDueReportDto build(int year, int month, List<Row> rows) {
        List<Row> sorted = new ArrayList<>(rows == null ? List.of() : rows);
        sorted.sort(Comparator.comparing(Row::date, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Row::side)
                .thenComparing(Row::counterparty, String.CASE_INSENSITIVE_ORDER)
                .thenComparingLong(Row::itemId));

        Period lifetime = period(sorted, Row::settled);
        Period yearTotals = period(sorted, row -> row.year() == year);
        Period monthTotals = period(sorted, row -> row.year() == year && row.month() == month);

        List<MonthBar> months = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            int focusMonth = m;
            Period p = period(sorted, row -> row.year() == year && row.month() == focusMonth);
            months.add(new MonthBar(
                    year,
                    m,
                    p.paid(),
                    p.received(),
                    p.net(),
                    p.openPayable(),
                    p.openReceivable(),
                    p.settledCount(),
                    p.openPayableCount() + p.openReceivableCount()));
        }

        return new ManagementDueReportDto(
                year,
                month,
                lifetime,
                yearTotals,
                monthTotals,
                List.copyOf(months),
                daysForYear(sorted, year),
                List.copyOf(sorted),
                who(sorted, row -> row.year() == year && row.month() == month));
    }

    static Period period(List<Row> rows, Predicate<Row> filter) {
        BigDecimal paid = zero();
        BigDecimal received = zero();
        BigDecimal openPayable = zero();
        BigDecimal openReceivable = zero();
        int openPayableCount = 0;
        int openReceivableCount = 0;
        int settledCount = 0;
        LocalDate firstSettledOn = null;
        String biggestOutName = null;
        BigDecimal biggestOutAmount = null;
        String biggestInName = null;
        BigDecimal biggestInAmount = null;
        String scheduledOutName = null;
        BigDecimal scheduledOutAmount = null;
        String scheduledInName = null;
        BigDecimal scheduledInAmount = null;

        for (Row row : rows) {
            if (!filter.test(row)) {
                continue;
            }
            BigDecimal amount = amountOf(row);
            boolean payable = "PAYABLE".equals(row.side());
            if (row.settled()) {
                settledCount += 1;
                if (row.date() != null && (firstSettledOn == null || row.date().isBefore(firstSettledOn))) {
                    firstSettledOn = row.date();
                }
                if (payable) {
                    paid = paid.add(amount);
                    if (biggestOutAmount == null || amount.compareTo(biggestOutAmount) > 0) {
                        biggestOutAmount = amount;
                        biggestOutName = row.counterparty();
                    }
                } else {
                    received = received.add(amount);
                    if (biggestInAmount == null || amount.compareTo(biggestInAmount) > 0) {
                        biggestInAmount = amount;
                        biggestInName = row.counterparty();
                    }
                }
            } else if (payable) {
                openPayable = openPayable.add(amount);
                openPayableCount += 1;
                if (scheduledOutAmount == null || amount.compareTo(scheduledOutAmount) > 0) {
                    scheduledOutAmount = amount;
                    scheduledOutName = row.counterparty();
                }
            } else {
                openReceivable = openReceivable.add(amount);
                openReceivableCount += 1;
                if (scheduledInAmount == null || amount.compareTo(scheduledInAmount) > 0) {
                    scheduledInAmount = amount;
                    scheduledInName = row.counterparty();
                }
            }
        }

        if (biggestOutName == null) {
            biggestOutName = scheduledOutName;
            biggestOutAmount = scheduledOutAmount;
        }
        if (biggestInName == null) {
            biggestInName = scheduledInName;
            biggestInAmount = scheduledInAmount;
        }

        return new Period(
                paid,
                received,
                received.subtract(paid),
                openPayable,
                openReceivable,
                openPayableCount,
                openReceivableCount,
                settledCount,
                firstSettledOn,
                biggestOutName,
                biggestOutAmount,
                biggestInName,
                biggestInAmount);
    }

    static List<Who> who(List<Row> rows, Predicate<Row> filter) {
        Map<String, Acc> byKey = new LinkedHashMap<>();
        for (Row row : rows) {
            if (!filter.test(row)) {
                continue;
            }
            String key = row.side() + "|" + row.counterparty().toLowerCase();
            Acc acc = byKey.computeIfAbsent(key, ignored -> new Acc(row.counterparty(), row.side()));
            BigDecimal amount = amountOf(row);
            if (row.settled()) {
                if ("PAYABLE".equals(row.side())) {
                    acc.paid = acc.paid.add(amount);
                } else {
                    acc.received = acc.received.add(amount);
                }
            } else {
                acc.open = acc.open.add(amount);
            }
            acc.count += 1;
            if (row.date() != null && (acc.last == null || row.date().isAfter(acc.last))) {
                acc.last = row.date();
            }
        }
        List<Who> out = new ArrayList<>();
        for (Acc acc : byKey.values()) {
            out.add(new Who(acc.counterparty, acc.side, acc.paid, acc.received, acc.open, acc.count, acc.last));
        }
        out.sort(Comparator.comparing((Who row) -> row.paid().add(row.received()).add(row.openAmount()))
                .reversed()
                .thenComparing(Who::counterparty, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(out);
    }

    private static List<Day> daysForYear(List<Row> rows, int year) {
        Map<LocalDate, AccDay> byDate = new LinkedHashMap<>();
        for (Row row : rows) {
            if (row.year() != year || row.date() == null) {
                continue;
            }
            AccDay acc = byDate.computeIfAbsent(row.date(), ignored -> new AccDay());
            BigDecimal amount = amountOf(row);
            if ("PAYABLE".equals(row.side())) {
                acc.paid = acc.paid.add(amount);
            } else {
                acc.received = acc.received.add(amount);
            }
            acc.count += 1;
        }
        List<Day> days = new ArrayList<>();
        for (Map.Entry<LocalDate, AccDay> entry : byDate.entrySet()) {
            AccDay acc = entry.getValue();
            days.add(new Day(entry.getKey(), acc.paid, acc.received, acc.received.subtract(acc.paid), acc.count));
        }
        days.sort(Comparator.comparing(Day::date));
        return List.copyOf(days);
    }

    private static BigDecimal amountOf(Row row) {
        return row.amount() == null ? zero() : row.amount().setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private static final class Acc {
        private final String counterparty;
        private final String side;
        private BigDecimal paid = zero();
        private BigDecimal received = zero();
        private BigDecimal open = zero();
        private int count;
        private LocalDate last;

        private Acc(String counterparty, String side) {
            this.counterparty = counterparty;
            this.side = side;
        }
    }

    private static final class AccDay {
        private BigDecimal paid = zero();
        private BigDecimal received = zero();
        private int count;
    }
}
