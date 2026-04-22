package com.svp.tracker.journal.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class JournalEntryDto {
    long id;
    long ownerUserId;
    LocalDate loggedOn;
    String bodyMarkdown;
    List<JournalTagDefDto> tags;
    @Builder.Default
    int attachmentCount = 0;
    List<JournalAttachmentDto> attachments;
    Instant createdAt;
    Instant updatedAt;
}
