package com.svp.tracker.finance.dto;

import java.util.List;

public record TradingJournalUpdateRequestDto(
        String title, String bodyMarkdown, List<String> tags, Integer processGrade, Integer riskGrade) {}
