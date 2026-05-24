package com.svp.tracker.management.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** User-defined type for Management → Calendar entries (code stored on report_calendar_entries). */
@Entity
@Table(name = "management_calendar_types")
@Getter
@Setter
@NoArgsConstructor
public class ManagementCalendarType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable key stored on calendar entries (e.g. BIRTHDAY, WORK). Uppercase letters, digits, underscore. */
    @NotBlank
    @Size(max = 32)
    @Column(nullable = false, length = 32)
    private String code;

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, length = 120)
    private String label;

    @Column(name = "sort_index", nullable = false)
    private int sortIndex = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
