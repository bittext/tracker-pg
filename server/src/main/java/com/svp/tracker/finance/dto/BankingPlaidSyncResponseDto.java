package com.svp.tracker.finance.dto;

public record BankingPlaidSyncResponseDto(
        int transactionsFetchedFromPlaid,
        int ofxStatementRows,
        String storedRelativePath,
        String absoluteDirectoryUnderImportRoot,
        BankingImportResultDto importResult) {}
