package com.svp.tracker.management.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ManagementDueReportDto(
        int year,
        int month,
        Period lifetime,
        Period yearTotals,
        Period monthTotals,
        List<MonthBar> months,
        List<Day> days,
        List<Row> rows,
        List<Who> who) {

    public record Period(
            BigDecimal paid,
            BigDecimal received,
            BigDecimal net,
            BigDecimal openPayable,
            BigDecimal openReceivable,
            int openPayableCount,
            int openReceivableCount,
            int settledCount,
            LocalDate firstSettledOn,
            String biggestOutName,
            BigDecimal biggestOutAmount,
            String biggestInName,
            BigDecimal biggestInAmount) {}

    public record MonthBar(
            int year,
            int month,
            BigDecimal paid,
            BigDecimal received,
            BigDecimal net,
            BigDecimal openPayable,
            BigDecimal openReceivable,
            int settledCount,
            int openCount) {}

    public record Day(
            LocalDate date, BigDecimal paid, BigDecimal received, BigDecimal net, int itemCount) {}

    public record Row(
            long itemId,
            Long occurrenceId,
            int year,
            int month,
            LocalDate date,
            String side,
            String counterparty,
            boolean recurring,
            String notes,
            boolean settled,
            BigDecimal amount,
            String amountSource) {}

    public record Who(
            String counterparty,
            String side,
            BigDecimal paid,
            BigDecimal received,
            BigDecimal openAmount,
            int count,
            LocalDate lastDate) {}
}
