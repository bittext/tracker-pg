package com.svp.tracker.finance.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record CompanyResearchCardDto(
        long id,
        String symbol,
        String companyName,
        String decisionStatus,
        List<String> tags,
        String thesis,
        LocalDate nextEarningsDate,
        String nextEarningsTiming,
        Instant lastViewedAt,
        Instant createdAt,
        Instant updatedAt,
        int noteCount) {}
