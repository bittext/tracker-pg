package com.svp.tracker.journal.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class JournalAttachmentDto {
    long id;
    String originalFilename;
    String contentType;
    Long sizeBytes;
    String downloadPath;
    /** Photo capture time when known (EXIF / filename); otherwise null. */
    Instant capturedAt;
    Instant createdAt;
}
