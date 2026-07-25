package com.svp.tracker.management.dto;

import java.time.LocalDate;

public record ManagementRecordingItemDto(
        String path,
        String displayName,
        LocalDate recordedDay,
        long fileSizeBytes,
        boolean hasTranscript,
        boolean hasSummary) {}
