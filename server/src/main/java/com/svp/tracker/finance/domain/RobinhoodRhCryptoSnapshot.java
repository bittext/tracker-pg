package com.svp.tracker.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "robinhood_rh_crypto_snapshot")
@Getter
@Setter
@NoArgsConstructor
public class RobinhoodRhCryptoSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "snapshot_at", nullable = false)
    private Instant snapshotAt;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "capture_kind", nullable = false, length = 16)
    private String captureKind = RobinhoodRhDailyCaptureKind.SCHEDULED;

    @Column(name = "total_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalValue;

    @Column(name = "holdings_json", nullable = false, columnDefinition = "TEXT")
    private String holdingsJson = "[]";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
