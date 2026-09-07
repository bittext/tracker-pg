package com.svp.tracker.management.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ManagementDueMonthDto(
        int year,
        int month,
        BigDecimal paidTotal,
        BigDecimal receivedTotal,
        BigDecimal netTotal,
        BigDecimal yearPaidTotal,
        BigDecimal yearReceivedTotal,
        BigDecimal yearNetTotal,
        List<Day> days,
        List<ManagementDueSuggestionDto> suggestions) {

    public record Day(
            LocalDate date,
            int payableCount,
            int receivableCount,
            BigDecimal payableTotal,
            BigDecimal receivableTotal,
            List<ManagementDueOccurrenceDto> items) {}
}
