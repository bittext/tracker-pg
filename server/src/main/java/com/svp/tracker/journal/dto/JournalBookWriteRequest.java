package com.svp.tracker.journal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record JournalBookWriteRequest(
        @NotBlank @Size(max = 512) String title,
        @Size(max = 256) String author,
        @NotBlank
                @Pattern(regexp = "TO_READ|READING|FINISHED", message = "status must be TO_READ, READING, or FINISHED")
                String status,
        @Size(max = 2048) String url,
        @Size(max = 500_000) String notesMarkdown,
        LocalDate startedOn,
        LocalDate finishedOn,
        @Min(1) @Max(5) Short rating) {}
