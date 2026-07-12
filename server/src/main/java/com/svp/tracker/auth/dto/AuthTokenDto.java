package com.svp.tracker.auth.dto;

import java.time.Instant;

public record AuthTokenDto(
        String token, Instant expiresAt, String username, String role, boolean marketsEnabled) {}
