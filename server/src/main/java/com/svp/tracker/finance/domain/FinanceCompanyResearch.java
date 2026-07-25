package com.svp.tracker.finance.domain;

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
@Table(name = "finance_company_research")
@Getter
@Setter
@NoArgsConstructor
public class FinanceCompanyResearch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(name = "company_name", nullable = false, length = 256)
    private String companyName = "";

    @Column(name = "decision_status", nullable = false, length = 24)
    private String decisionStatus = "WATCHING";

    @Column(nullable = false, length = 512)
    private String tags = "";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String thesis = "";

    @Column(name = "next_earnings_date")
    private LocalDate nextEarningsDate;

    @Column(name = "next_earnings_timing", length = 32)
    private String nextEarningsTiming;

    @Column(name = "last_viewed_at")
    private Instant lastViewedAt;

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
