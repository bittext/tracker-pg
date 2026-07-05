package com.svp.tracker.finance.dto;

import java.time.Instant;

public record RobinhoodRhCryptoCaptureResultDto(
        boolean ok, Instant capturedAt, String message, int holdingsCaptured) {}
