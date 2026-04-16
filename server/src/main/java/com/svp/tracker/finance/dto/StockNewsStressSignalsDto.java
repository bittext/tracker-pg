package com.svp.tracker.finance.dto;

/** Stress-signals extracted from recent validated headlines. */
public record StockNewsStressSignalsDto(
        int mergerMentions,
        int acquisitionMentions,
        int dealMentions,
        int permitMentions,
        int sanctionMentions,
        String emphasis) {}
