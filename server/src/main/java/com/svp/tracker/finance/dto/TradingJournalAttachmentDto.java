package com.svp.tracker.finance.dto;

import java.time.Instant;

public record TradingJournalAttachmentDto(
        long id,
        String originalFilename,
        String contentType,
        Long sizeBytes,
        String downloadPath,
        Instant createdAt,
        /** Capture/taken time when known; otherwise same as {@code createdAt}. */
        Instant capturedAt) {}
