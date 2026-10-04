package com.svp.tracker.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "banking_plaid_balance_snapshots",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_plaid_balance_owner_inst_day_acct",
                        columnNames = {"owner_user_id", "institution_id", "snapshot_date", "plaid_account_id"}))
@Getter
@Setter
@NoArgsConstructor
public class BankingPlaidBalanceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "institution_id", nullable = false)
    private Long institutionId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "plaid_account_id", nullable = false, columnDefinition = "TEXT")
    private String plaidAccountId;

    @Column(name = "account_label", nullable = false, columnDefinition = "TEXT")
    private String accountLabel;

    @Column(name = "current_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal currentBalance;

    @Column(name = "available_balance", precision = 19, scale = 2)
    private BigDecimal availableBalance;

    @Column(name = "iso_currency", length = 8)
    private String isoCurrency;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt = Instant.now();
}
