package com.svp.tracker.management.dto;

import java.time.Instant;
import java.time.LocalDate;

public record ManagementRecordingDetailDto(
        String path,
        String displayName,
        LocalDate recordedDay,
        long fileSizeBytes,
        String transcript,
        String transcriptSource,
        Instant transcribedAt,
        String summary,
        Instant summarizedAt) {}
