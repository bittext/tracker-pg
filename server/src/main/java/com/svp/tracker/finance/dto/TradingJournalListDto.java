package com.svp.tracker.finance.dto;

import java.time.LocalDate;
import java.util.List;

public record TradingJournalListDto(
        int year,
        Integer month,
        String q,
        List<TradingJournalEntrySummaryDto> entries,
        List<LocalDate> journalDates,
        /** Per-day Δ prior close for the selected month (empty when month is "all"). */
        List<TradingJournalCalendarDayDto> calendarDays) {}
