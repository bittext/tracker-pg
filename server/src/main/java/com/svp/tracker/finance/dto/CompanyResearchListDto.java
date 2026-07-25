package com.svp.tracker.finance.dto;

import java.util.List;

public record CompanyResearchListDto(List<CompanyResearchCardDto> cards, int total) {}
