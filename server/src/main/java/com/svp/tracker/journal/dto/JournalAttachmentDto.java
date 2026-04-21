package com.svp.tracker.journal.dto;

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
}
