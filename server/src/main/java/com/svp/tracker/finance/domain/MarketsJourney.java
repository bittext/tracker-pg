package com.svp.tracker.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "markets_journeys")
@Getter
@Setter
@NoArgsConstructor
public class MarketsJourney {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "milestone_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal milestoneAmount;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Day a chapter began; null for journeys that follow the full Daily Tracker history. */
    @Column(name = "start_date")
    private LocalDate startDate;

    /** Net across every account in the snapshot the chapter started from. */
    @Column(name = "start_amount", precision = 18, scale = 2)
    private BigDecimal startAmount;

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
