package com.svp.tracker.finance.dto;

import java.time.LocalDate;
import java.util.List;

public record TradingJournalListDto(
        int year, Integer month, String q, List<TradingJournalEntrySummaryDto> entries, List<LocalDate> journalDates) {}
