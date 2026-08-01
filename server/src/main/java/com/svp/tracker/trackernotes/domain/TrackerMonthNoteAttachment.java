package com.svp.tracker.trackernotes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tracker_month_note_attachments")
@Getter
@Setter
@NoArgsConstructor
public class TrackerMonthNoteAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "note_id", nullable = false)
    private TrackerMonthNote note;

    @Column(name = "storage_key", nullable = false, columnDefinition = "TEXT")
    private String storageKey = "";

    @Column(name = "original_filename", nullable = false, columnDefinition = "TEXT")
    private String originalFilename = "";

    @Column(name = "content_type", columnDefinition = "TEXT")
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
