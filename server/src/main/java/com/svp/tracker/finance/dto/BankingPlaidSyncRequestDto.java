package com.svp.tracker.finance.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record BankingPlaidSyncRequestDto(
        @NotNull Long institutionId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        /** When set, only these Plaid {@code account_id} values are pulled; when null/empty, all accounts on the Item. */
        List<String> accountIds) {}
