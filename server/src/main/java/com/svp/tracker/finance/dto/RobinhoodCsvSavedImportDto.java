package com.svp.tracker.finance.dto;

/**
 * Result of saving an uploaded CSV into {@link com.svp.tracker.config.FinanceProperties#robinhoodCsvImportDirectory()}
 * and running the same import pipeline as {@code POST /import-csv}.
 */
public record RobinhoodCsvSavedImportDto(
        /** Normalized configured import directory (container/host mount path). */
        String importDirectory,
        /** Absolute path of the written file. */
        String savedAbsolutePath,
        RobinhoodCsvImportResultDto importResult) {}
