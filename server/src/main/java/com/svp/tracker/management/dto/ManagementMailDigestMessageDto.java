package com.svp.tracker.management.dto;

import java.time.Instant;

public record ManagementMailDigestMessageDto(
        long id,
        String provider,
        String mailbox,
        String from,
        String subject,
        Instant receivedAt,
        String snippet) {}
