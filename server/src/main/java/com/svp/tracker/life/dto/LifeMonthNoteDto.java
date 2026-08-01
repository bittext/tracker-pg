package com.svp.tracker.life.dto;

import java.time.Instant;
import java.util.List;

public record LifeMonthNoteDto(
        long id,
        long ownerUserId,
        int year,
        int month,
        String subject,
        String body,
        List<LifeMonthNoteAttachmentDto> attachments,
        Instant createdAt,
        Instant updatedAt) {}
