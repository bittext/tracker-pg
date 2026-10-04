package com.svp.tracker.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ManagementDueCategoryWriteRequest(@NotBlank @Size(max = 80) String name) {}
