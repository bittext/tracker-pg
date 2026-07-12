package com.svp.tracker.admin.dto;

public record AdminUpdateUserRequestDto(Boolean active, Boolean marketsEnabled, Boolean mfaEnabled) {}
