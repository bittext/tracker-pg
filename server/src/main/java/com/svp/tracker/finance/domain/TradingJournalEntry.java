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
@Table(name = "trading_journal_entry")
@Getter
@Setter
@NoArgsConstructor
public class TradingJournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(nullable = false, length = 256)
    private String title = "";

    @Column(name = "body_markdown", nullable = false, columnDefinition = "TEXT")
    private String bodyMarkdown = "";

    @Column(nullable = false, length = 512)
    private String tags = "";

    @Column(name = "process_grade")
    private Integer processGrade;

    @Column(name = "risk_grade")
    private Integer riskGrade;

    @Column(name = "linked_summary_note", nullable = false)
    private boolean linkedSummaryNote;

    @Column(name = "has_scheduled_close", nullable = false)
    private boolean hasScheduledClose;

    @Column(name = "close_combined_total", precision = 18, scale = 2)
    private BigDecimal closeCombinedTotal;

    @Column(name = "close_combined_change", precision = 18, scale = 2)
    private BigDecimal closeCombinedChange;

    @Column(name = "close_pulled_at")
    private Instant closePulledAt;

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
