package com.svp.tracker.management.dto;

import java.time.Instant;

public record ManagementRecordingImageDto(
        long id, String originalFilename, String contentType, long sizeBytes, Instant createdAt) {}
