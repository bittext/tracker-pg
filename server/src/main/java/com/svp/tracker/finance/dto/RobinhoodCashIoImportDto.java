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
        List<Row> removals,
        List<Skipped> skipped,
        int newCount,
        int duplicateCount,
        int removeCount,
        int ignoredCount,
        int imported,
        int removed) {

    /** One transfer found in the file. {@code status} is NEW, DUPLICATE (already in the ledger) or REMOVE
     * (an entry added by an earlier import whose transfer has since failed, been rejected or expired). */
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
