package com.svp.tracker.management.dto;

import java.time.Instant;
import java.util.List;

public record ManagementMonthNoteDto(
        long id,
        long ownerUserId,
        int year,
        int month,
        String subject,
        String body,
        List<ManagementMonthNoteAttachmentDto> attachments,
        Instant createdAt,
        Instant updatedAt) {}
