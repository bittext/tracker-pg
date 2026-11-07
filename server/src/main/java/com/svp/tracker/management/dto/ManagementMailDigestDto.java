package com.svp.tracker.management.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ManagementMailDigestDto(
        long id,
        LocalDate digestOn,
        String slot,
        String slotLabel,
        Instant createdAt,
        String summary,
        int messageCount,
        String status,
        List<ManagementMailDigestMessageDto> messages) {}
