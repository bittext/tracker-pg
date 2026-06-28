package com.svp.tracker.finance.dto;

import java.time.Instant;

public record RobinhoodRhDailyCaptureResultDto(
        boolean ok, Instant capturedAt, int accountsCaptured, String message) {}
