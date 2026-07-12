package com.svp.tracker.auth.dto;

import java.time.Instant;

public record StepUpTokenDto(String stepUpToken, Instant expiresAt) {}
