package com.svp.tracker.management.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ManagementDayOneAttachmentDto {
    Long id;
    String originalFilename;
    String contentType;
    Long sizeBytes;
    /** Relative API path to download (includes attachment id). */
    String downloadPath;
}
