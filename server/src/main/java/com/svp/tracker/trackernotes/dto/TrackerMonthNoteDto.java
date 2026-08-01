package com.svp.tracker.trackernotes.dto;

import java.time.Instant;
import java.util.List;

public record TrackerMonthNoteDto(
        long id,
        long ownerUserId,
        int year,
        int month,
        String subject,
        String body,
        List<TrackerMonthNoteAttachmentDto> attachments,
        Instant createdAt,
        Instant updatedAt) {}
