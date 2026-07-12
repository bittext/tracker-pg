package com.svp.tracker.auth.dto;

/** Session snapshot for the signed-in user (GET /api/auth/me). */
public record MeSessionDto(
        long userId, String username, String role, boolean marketsEnabled, boolean admin) {}
