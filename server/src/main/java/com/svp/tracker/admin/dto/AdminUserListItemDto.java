package com.svp.tracker.admin.dto;

public record AdminUserListItemDto(
        long id,
        String username,
        String email,
        String role,
        boolean active,
        boolean mfaEnabled,
        boolean marketsEnabled) {}
