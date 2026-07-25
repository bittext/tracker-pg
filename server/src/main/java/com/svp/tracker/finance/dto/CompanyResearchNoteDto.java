package com.svp.tracker.finance.dto;

import java.time.Instant;
import java.util.List;

public record CompanyResearchNoteDto(
        long id, long researchId, String symbol, String noteText, List<String> tags, Instant createdAt, Instant updatedAt) {}
