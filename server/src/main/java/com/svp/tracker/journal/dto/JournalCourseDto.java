package com.svp.tracker.journal.dto;

import java.time.Instant;
import java.time.LocalDate;

public record JournalCourseDto(
        Long id,
        String title,
        String provider,
        String status,
        String url,
        String notesMarkdown,
        LocalDate startedOn,
        LocalDate completedOn,
        Instant createdAt,
        Instant updatedAt) {}
