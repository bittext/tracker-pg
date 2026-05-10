package com.svp.tracker.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BankingPutInstitutionRequestDto(
        @NotBlank @Size(max = 256) String name, Long institutionTypeId) {}
