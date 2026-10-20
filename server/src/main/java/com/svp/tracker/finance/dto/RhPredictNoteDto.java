package com.svp.tracker.finance.dto;

import java.time.Instant;

public record RhPredictNoteDto(long id, String body, Instant createdAt) {}
