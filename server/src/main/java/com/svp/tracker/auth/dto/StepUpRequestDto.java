package com.svp.tracker.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StepUpRequestDto(@NotBlank @Size(min = 1, max = 200) String password) {}
