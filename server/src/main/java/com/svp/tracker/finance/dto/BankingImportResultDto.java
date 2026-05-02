package com.svp.tracker.finance.dto;

public record BankingImportResultDto(
        boolean success,
        boolean skippedDuplicateFile,
        BankingImportFileDto file,
        String message) {}
