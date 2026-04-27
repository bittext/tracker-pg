package com.svp.tracker.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "finance_tax_1040_returns",
        uniqueConstraints = @UniqueConstraint(columnNames = {"owner_user_id", "tax_year"}))
@Getter
@Setter
@NoArgsConstructor
public class FinanceTax1040Return {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "tax_year", nullable = false)
    private int taxYear;

    @Column(name = "storage_key", nullable = false, columnDefinition = "TEXT")
    private String storageKey = "";

    @Column(name = "original_filename", nullable = false, columnDefinition = "TEXT")
    private String originalFilename = "";

    @Column(name = "content_type", columnDefinition = "TEXT")
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    @Column(name = "summary_json", nullable = false, columnDefinition = "TEXT")
    private String summaryJson = "{}";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
