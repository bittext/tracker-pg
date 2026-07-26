package com.svp.tracker.finance.dto.finviz;

public record FinvizElitePresetDto(
        String id,
        String label,
        String category,
        String description,
        String signal,
        String filters,
        String view) {}
