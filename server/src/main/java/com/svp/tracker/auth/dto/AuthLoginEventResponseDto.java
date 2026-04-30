package com.svp.tracker.auth.dto;

import java.time.Instant;

public record AuthLoginEventResponseDto(
        long id,
        String eventType,
        Long userId,
        String username,
        String clientIp,
        String userAgent,
        String detail,
        Instant createdAt) {}
