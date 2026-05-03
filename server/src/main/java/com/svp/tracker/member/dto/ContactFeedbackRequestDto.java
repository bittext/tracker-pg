package com.svp.tracker.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContactFeedbackRequestDto(
        @Size(max = 80) String displayName,
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(max = 12000) String details) {}
