package com.svp.tracker.finance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BankingCreateInstitutionTypeRequestDto(
        @NotBlank @Size(max = 256) String name,
        @Min(0) @Max(999_999) Integer sortOrder) {}
