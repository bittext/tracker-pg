package com.svp.tracker.management.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "management_recording_cache")
@Getter
@Setter
@NoArgsConstructor
public class ManagementRecordingCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "relative_path", nullable = false, length = 1024)
    private String relativePath;

    @Column(name = "display_name", nullable = false, length = 512)
    private String displayName;

    @Column(name = "recorded_day")
    private LocalDate recordedDay;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    /** Journal blob store key for the audio payload (null = metadata-only / legacy). */
    @Column(name = "storage_key", length = 1024)
    private String storageKey;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "original_filename", length = 512)
    private String originalFilename;

    @Column(columnDefinition = "TEXT")
    private String transcript;

    @Column(name = "transcript_source", length = 64)
    private String transcriptSource;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "transcribed_at")
    private Instant transcribedAt;

    @Column(name = "summarized_at")
    private Instant summarizedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
