package com.svp.tracker.management.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ManagementWorkLogEntryWriteRequest(
        @NotNull LocalDate entryDate,
        /** Display title; trimmed on save. */
        String subject,
        /** Markdown body; may be empty. */
        String body) {}
