package com.svp.tracker.management.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** On-the-job work log line; multiple rows allowed per owner and {@link #entryDate}. */
@Entity
@Table(name = "management_work_log_entries")
@Getter
@Setter
@NoArgsConstructor
public class ManagementWorkLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @NotNull
    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @NotNull
    @Column(name = "logged_at", nullable = false)
    private Instant loggedAt = Instant.now();

    @Column(nullable = false, length = 500)
    private String subject = "";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body = "";

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
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (loggedAt == null) {
            loggedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
