package com.svp.tracker.finance.dto;

import com.svp.tracker.finance.tax.Form1040ParsedSummary;
import java.time.Instant;

public record FinanceTax1040ReturnDto(
        long id,
        int taxYear,
        String originalFilename,
        long sizeBytes,
        String downloadPath,
        Form1040ParsedSummary summary,
        String extractedTextPreview,
        String extractedTextFull,
        Instant createdAt,
        Instant updatedAt) {}
