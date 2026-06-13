package com.svp.tracker.finance.dto;

import java.time.Instant;

public record FinanceEntryDocumentDto(
        long id,
        String entityType,
        long entityId,
        String originalFilename,
        String displayName,
        String contentType,
        long sizeBytes,
        Instant createdAt) {}
