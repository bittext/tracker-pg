package com.svp.tracker.finance.dto;

/** One file from {@link RobinhoodCsvDirectoryImportDto}. */
public record RobinhoodCsvDirectoryImportFileDto(
        /** Absolute path of the file after move when apply succeeded with no errors; otherwise null. */
        String movedToPath,
        RobinhoodCsvImportResultDto importResult) {}
