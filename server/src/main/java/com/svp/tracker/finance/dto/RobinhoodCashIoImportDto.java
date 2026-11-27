package com.svp.tracker.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** What an import would add (preview) or did add (import). {@code accountSuffix} is null for all accounts. */
public record RobinhoodCashIoImportDto(
        String format,
        String accountSuffix,
        LocalDate periodFrom,
        LocalDate periodTo,
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
            String accountSuffix,
            String accountLabel,
            String direction,
            BigDecimal amount,
            String counterparty,
            String note,
            String status) {}

    /** A transfer-like row that was left out, with the reason. */
    public record Skipped(String source, String reason) {}
}
