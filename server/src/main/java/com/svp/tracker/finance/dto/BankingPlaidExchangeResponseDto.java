package com.svp.tracker.finance.dto;

import java.util.List;

public record BankingPlaidExchangeResponseDto(
        long institutionId,
        /** Institution row name after applying Plaid-based rename (unique per owner). */
        String institutionName,
        boolean institutionRenamedFromPlaid,
        /** One line per linked account / connection detail. */
        List<String> connectionSummary,
        /** All banking institution ids created or updated for this Plaid Item (anchor first). */
        List<Long> linkedInstitutionIds) {}
