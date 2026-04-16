package com.svp.tracker.auth.dto;

public record LoginResponseDto(boolean mfaRequired, String challengeId, String message, AuthTokenDto token) {}
