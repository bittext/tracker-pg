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
@Table(name = "finance_loans")
@Getter
@Setter
@NoArgsConstructor
public class FinanceLoan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(nullable = false)
    private String institution;

    @Column(name = "loan_nature", nullable = false, length = 64)
    private String loanNature;

    @Column(name = "nature_other")
    private String natureOther;

    @Column(name = "date_availed")
    private LocalDate dateAvailed;

    @Column(name = "date_to_commence")
    private LocalDate dateToCommence;

    @Column(name = "current_balance", precision = 19, scale = 2)
    private BigDecimal currentBalance;

    @Column(name = "interest_rate", precision = 8, scale = 4)
    private BigDecimal interestRate;

    @Column(name = "paid_so_far", precision = 19, scale = 2)
    private BigDecimal paidSoFar;

    @Column(name = "balance_to_pay", precision = 19, scale = 2)
    private BigDecimal balanceToPay;

    @Column(name = "payment_frequency", nullable = false, length = 32)
    private String paymentFrequency = "MONTHLY";

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
