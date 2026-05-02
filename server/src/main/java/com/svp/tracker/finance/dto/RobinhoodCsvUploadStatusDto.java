package com.svp.tracker.finance.dto;

/** Whether CSV uploads to the import folder are enabled ({@code tracker.finance.robinhood-csv-import-directory}). */
public record RobinhoodCsvUploadStatusDto(boolean configured, String importDirectory) {}
