package com.svp.tracker.finance.dto;

import java.util.List;
import java.util.Map;

/** Result payload for local Robinhood CSV import endpoint. */
public record RobinhoodCsvImportResultDto(
        boolean apply,
        String fileName,
        int csvRowCount,
        int parsedRows,
        int insertedRows,
        /** Rows skipped because they duplicate an earlier row in the file or an existing DB row (when deduplication is on). */
        int duplicateRowsSkipped,
        int skippedRows,
        int errorCount,
        List<String> errors,
        List<String> detectedHeaders,
        List<String> detectedInstruments,
        List<Map<String, String>> previewRows,
        String tableTarget,
        String note) {}
