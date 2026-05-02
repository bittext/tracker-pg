package com.svp.tracker.finance.dto;

public record BankingImportFileDto(
        long id,
        long institutionId,
        String institutionName,
        String fileKind,
        String originalFilename,
        String contentType,
        String sha256Hex,
        long sizeBytes,
        boolean skippedDuplicateFile,
        int rowsInserted,
        int rowsSkippedDuplicate,
        String parseNote,
        String createdAt) {}
