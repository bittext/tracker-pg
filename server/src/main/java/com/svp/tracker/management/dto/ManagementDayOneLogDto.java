package com.svp.tracker.management.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ManagementDayOneLogDto {
    Long id;
    Long ownerUserId;
    LocalDate loggedOn;
    String entryText;
    String locationText;
    String weatherText;
    List<ManagementDayOneTagDefDto> tags;
    List<ManagementDayOneAttachmentDto> attachments;
    Instant createdAt;
    Instant updatedAt;
}
