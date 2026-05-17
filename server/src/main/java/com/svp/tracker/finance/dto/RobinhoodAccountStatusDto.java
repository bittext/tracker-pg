package com.svp.tracker.finance.dto;

import java.time.LocalDate;

/** Snapshot of imported Robinhood data for the signed-in user. */
public record RobinhoodAccountStatusDto(
        String tableQueried,
        long transactionRowCount,
        LocalDate earliestActivity,
        LocalDate latestActivity,
        boolean csvImportDirectoryConfigured,
        String csvImportDirectory) {}
