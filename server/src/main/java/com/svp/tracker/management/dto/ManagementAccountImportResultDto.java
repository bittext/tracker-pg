package com.svp.tracker.management.dto;

public record ManagementAccountImportResultDto(int submitted, int inserted, int skippedDuplicates) {}
