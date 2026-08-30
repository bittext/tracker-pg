package com.svp.tracker.finance.dto;

import java.util.List;

public record SymbolSearchResponseDto(
        String query, List<SymbolSearchMatchDto> matches, boolean autoSelected) {}
