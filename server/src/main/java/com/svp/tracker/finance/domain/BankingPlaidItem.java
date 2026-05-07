package com.svp.tracker.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "banking_plaid_items")
@Getter
@Setter
@NoArgsConstructor
public class BankingPlaidItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private BankingInstitution institution;

    @Column(name = "item_id", nullable = false, columnDefinition = "TEXT")
    private String itemId;

    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /** Plaid institution id from Item (e.g. ins_...) */
    @Column(name = "plaid_institution_id", columnDefinition = "TEXT")
    private String plaidInstitutionId;

    /** JSON array of human-readable account/connection lines for the UI */
    @Column(name = "connection_summary", columnDefinition = "TEXT")
    private String connectionSummary;

    /** When set, Plaid transactions sync is limited to this account_id within the Item */
    @Column(name = "plaid_account_id", columnDefinition = "TEXT")
    private String plaidAccountId;
}
