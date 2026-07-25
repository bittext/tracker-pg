package com.svp.tracker.finance.dto;

import java.time.Instant;

public record TradingJournalRefDto(
        long id, String kind, String symbol, String url, String label, Instant createdAt) {}
