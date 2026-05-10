package com.svp.tracker.management.dto;

import java.time.Instant;
import java.time.LocalDate;

public record ManagementWorkLogEntryDto(
        long id,
        long ownerUserId,
        LocalDate entryDate,
        Instant loggedAt,
        String subject,
        String body,
        Instant createdAt,
        Instant updatedAt) {}
