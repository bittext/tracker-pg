package com.svp.tracker.finance.dto;

import java.time.Instant;

public record TradingJournalAttachmentDto(
        long id,
        String originalFilename,
        String contentType,
        Long sizeBytes,
        String downloadPath,
        Instant createdAt) {}
