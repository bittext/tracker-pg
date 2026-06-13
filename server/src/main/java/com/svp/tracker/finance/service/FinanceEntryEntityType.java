package com.svp.tracker.finance.service;

import com.svp.tracker.finance.dto.FinanceEntryDocumentDto;

public enum FinanceEntryEntityType {
    INVESTMENT,
    LOAN,
    CREDIT_CARD,
    INSURANCE;

    public static FinanceEntryEntityType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("entityType is required");
        }
        try {
            return FinanceEntryEntityType.valueOf(raw.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown entityType: " + raw);
        }
    }

    public String wire() {
        return name();
    }
}
