package com.svp.tracker.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ManagementDocumentWriteRequest(
        @NotBlank @Size(max = 512) String displayName,
        @NotBlank @Size(max = 64) String docType) {}
