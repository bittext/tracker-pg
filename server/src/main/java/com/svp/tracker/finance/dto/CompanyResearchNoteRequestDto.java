package com.svp.tracker.finance.dto;

import java.util.List;

public record CompanyResearchNoteRequestDto(String noteText, List<String> tags) {}
