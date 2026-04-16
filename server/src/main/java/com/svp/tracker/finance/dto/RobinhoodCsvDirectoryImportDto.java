package com.svp.tracker.finance.dto;

import java.util.List;

/** Result of {@code POST /api/finance/robinhood/import-csv-directory}. */
public record RobinhoodCsvDirectoryImportDto(
        boolean apply,
        String importDirectory,
        String uploadedDirectory,
        int csvFilesFound,
        List<RobinhoodCsvDirectoryImportFileDto> files,
        String note) {}
