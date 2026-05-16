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

/** User-defined type for Management → Now roadmap cards (slug, badge text, color). */
@Entity
@Table(name = "management_now_card_types")
@Getter
@Setter
@NoArgsConstructor
public class ManagementNowCardType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable key stored on cards (e.g. product, ops). Lowercase letters, digits, hyphen. */
    @NotBlank
    @Size(max = 64)
    @Column(nullable = false, length = 64)
    private String slug;

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, length = 120)
    private String label;

    /** Short text shown on the card (often lowercase). */
    @NotBlank
    @Size(max = 32)
    @Column(nullable = false, length = 32)
    private String badge;

    /** CSS color, e.g. #6366f1 */
    @NotBlank
    @Size(max = 16)
    @Column(name = "color_hex", nullable = false, length = 16)
    private String colorHex;

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
