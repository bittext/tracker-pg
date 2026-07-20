package com.svp.tracker.journal.dto;

import java.time.Instant;
import java.time.LocalDate;

public record JournalBookDto(
        Long id,
        String title,
        String author,
        String status,
        String url,
        String notesMarkdown,
        LocalDate startedOn,
        LocalDate finishedOn,
        Short rating,
        Instant createdAt,
        Instant updatedAt) {}
