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
@Table(name = "robinhood_agentic_banking_connections")
@Getter
@Setter
@NoArgsConstructor
public class RobinhoodAgenticBankingConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false, unique = true)
    private Long ownerUserId;

    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "card_last_four", length = 4)
    private String cardLastFour;

    @Column(name = "card_status", length = 32)
    private String cardStatus;

    @Column(name = "activation_status", length = 32)
    private String activationStatus;

    @Column(name = "monthly_limit_micro")
    private Long monthlyLimitMicro;

    @Column(name = "total_spend_micro")
    private Long totalSpendMicro;

    @Column(name = "available_balance_micro")
    private Long availableBalanceMicro;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Column(name = "last_sync_status", length = 32)
    private String lastSyncStatus;

    @Column(name = "last_sync_message", columnDefinition = "TEXT")
    private String lastSyncMessage;

    @Column(name = "snapshot_json", columnDefinition = "TEXT")
    private String snapshotJson;
}
