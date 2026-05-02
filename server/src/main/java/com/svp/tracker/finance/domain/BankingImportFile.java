package com.svp.tracker.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "banking_import_files")
@Getter
@Setter
@NoArgsConstructor
public class BankingImportFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private BankingInstitution institution;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_kind", nullable = false, length = 16)
    private BankingFileKind fileKind = BankingFileKind.DATA;

    @Column(name = "original_filename", nullable = false, columnDefinition = "TEXT")
    private String originalFilename = "";

    @Column(name = "content_type", columnDefinition = "TEXT")
    private String contentType;

    @Column(name = "sha256_hex", nullable = false, length = 64)
    private String sha256Hex = "";

    @Column(name = "stored_relative_path", nullable = false, columnDefinition = "TEXT")
    private String storedRelativePath = "";

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "skipped_duplicate_file", nullable = false)
    private boolean skippedDuplicateFile;

    @Column(name = "rows_inserted", nullable = false)
    private int rowsInserted;

    @Column(name = "rows_skipped_duplicate", nullable = false)
    private int rowsSkippedDuplicate;

    @Column(name = "parse_note", columnDefinition = "TEXT")
    private String parseNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
