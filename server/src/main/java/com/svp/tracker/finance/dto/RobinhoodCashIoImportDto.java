package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** What an import would add (preview) or did add (import) for one account. */
public record RobinhoodCashIoImportDto(
        String format,
        String accountSuffix,
        String dateMode,
        List<Row> rows,
        List<Skipped> skipped,
        int newCount,
        int duplicateCount,
        int ignoredCount,
        int imported) {

    /** One transfer found in the file. {@code status} is NEW or DUPLICATE (already in the ledger). */
    public record Row(
            LocalDate date,
            LocalDate requestedDate,
            LocalDate completedDate,
            String direction,
            BigDecimal amount,
            String counterparty,
            String note,
            String status) {}

    /** A transfer-like row that was left out, with the reason. */
    public record Skipped(String source, String reason) {}
}
