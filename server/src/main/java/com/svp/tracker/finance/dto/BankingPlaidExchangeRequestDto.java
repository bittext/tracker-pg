package com.svp.tracker.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BankingPlaidExchangeRequestDto(
        @NotNull Long institutionId, @NotBlank String publicToken) {}
