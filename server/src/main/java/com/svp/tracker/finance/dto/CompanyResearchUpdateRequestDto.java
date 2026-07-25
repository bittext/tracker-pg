package com.svp.tracker.finance.dto;

import java.util.List;

public record CompanyResearchUpdateRequestDto(
        String companyName, String decisionStatus, List<String> tags, String thesis) {}
