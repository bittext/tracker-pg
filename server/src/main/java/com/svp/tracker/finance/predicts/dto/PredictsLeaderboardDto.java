package com.svp.tracker.finance.predicts.dto;

import java.time.Instant;
import java.util.List;

public record PredictsLeaderboardDto(
        String type, Instant generatedAt, List<PredictsLeaderboardEntryDto> entries) {}
