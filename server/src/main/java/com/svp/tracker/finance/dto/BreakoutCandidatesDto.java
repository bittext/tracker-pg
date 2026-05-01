package com.svp.tracker.finance.dto;

import java.util.List;

public record BreakoutCandidatesDto(
        String source, String fetchedAt, int returned, String note, List<BreakoutCandidateRowDto> rows) {}
