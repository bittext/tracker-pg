package com.svp.tracker.finance.dto;

import java.time.LocalDate;

/**
 * A Robinhood CSV to import. Dates always follow the date each transfer was requested. A blank
 * {@code accountSuffix} (or "ALL") imports every account named in the file; {@code periodFrom} and
 * {@code periodTo} (both optional, inclusive) limit the import to that stretch of requested dates.
 */
public record RobinhoodCashIoImportRequestDto(
        String csv, String accountSuffix, LocalDate periodFrom, LocalDate periodTo) {}
