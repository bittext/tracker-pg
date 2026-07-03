package com.svp.tracker.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "robinhood_agentic_banking_transactions")
@Getter
@Setter
@NoArgsConstructor
public class RobinhoodAgenticBankingTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "external_id", nullable = false, columnDefinition = "TEXT")
    private String externalId;

    @Column(name = "merchant_name", columnDefinition = "TEXT")
    private String merchantName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "amount_micro")
    private Long amountMicro;

    @Column(name = "transaction_status", length = 32)
    private String transactionStatus;

    @Column(name = "transaction_at")
    private Instant transactionAt;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt = Instant.now();
}
