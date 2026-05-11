package com.svp.tracker.management.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * One-time migration body: clients (browser) post their accumulated localStorage entries so the server can persist
 * them per owner. Entries that already exist (same folder + item_name, case-insensitive) are skipped.
 */
public record ManagementAccountImportRequest(@NotNull @Valid List<ManagementAccountWriteRequest> entries) {}
