package com.svp.tracker.journal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "journal_courses")
@Getter
@Setter
@NoArgsConstructor
public class JournalCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @NotBlank
    @Column(nullable = false, length = 512)
    private String title = "";

    @Column(length = 256)
    private String provider;

    @Column(nullable = false, length = 32)
    private String status = "IN_PROGRESS";

    @Column(columnDefinition = "TEXT")
    private String url;

    @Column(name = "notes_markdown", nullable = false, columnDefinition = "TEXT")
    private String notesMarkdown = "";

    @Column(name = "started_on")
    private LocalDate startedOn;

    @Column(name = "completed_on")
    private LocalDate completedOn;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
