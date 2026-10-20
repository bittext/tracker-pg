package com.svp.tracker.finance.dto;

import java.time.Instant;
import java.util.List;

public record RhPredictFeedbackDto(String headline, List<String> points, String watch, Instant updatedAt) {}
