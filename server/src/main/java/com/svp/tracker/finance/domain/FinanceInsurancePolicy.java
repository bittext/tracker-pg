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
@Table(name = "finance_insurance_policies")
@Getter
@Setter
@NoArgsConstructor
public class FinanceInsurancePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(nullable = false)
    private String carrier;

    @Column(name = "policy_type", nullable = false, length = 64)
    private String policyType;

    @Column(name = "type_other")
    private String typeOther;

    @Column(name = "policy_number", length = 128)
    private String policyNumber;

    @Column(name = "coverage_description", nullable = false)
    private String coverageDescription;

    @Column(name = "premium_amount", precision = 19, scale = 2)
    private BigDecimal premiumAmount;

    @Column(name = "premium_frequency", nullable = false, length = 32)
    private String premiumFrequency = "ANNUAL";

    @Column(name = "coverage_start_date")
    private LocalDate coverageStartDate;

    @Column(name = "coverage_end_date")
    private LocalDate coverageEndDate;

    @Column(name = "renewal_reminder_days", nullable = false)
    private Integer renewalReminderDays = 30;

    @Column(columnDefinition = "TEXT")
    private String notes;

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
