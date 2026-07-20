package com.svp.tracker.journal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record JournalCourseWriteRequest(
        @NotBlank @Size(max = 512) String title,
        @Size(max = 256) String provider,
        @NotBlank
                @Pattern(regexp = "INTEND|IN_PROGRESS|COMPLETED", message = "status must be INTEND, IN_PROGRESS, or COMPLETED")
                String status,
        @Size(max = 2048) String url,
        @Size(max = 500_000) String notesMarkdown,
        LocalDate startedOn,
        LocalDate completedOn) {}
