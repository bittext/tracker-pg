package com.svp.tracker.finance.dto;

import java.time.LocalDate;
import java.util.List;

/** Composed day detail: journal entry + live Daily Tracker wrap. */
public record TradingJournalDayDetailDto(
        LocalDate snapshotDate,
        TradingJournalEntryDto entry,
        RobinhoodRhDailyTrackerDayDto wrap,
        boolean aiDraftAvailable,
        List<TradingJournalAccountHoldingsDto> holdings) {}
