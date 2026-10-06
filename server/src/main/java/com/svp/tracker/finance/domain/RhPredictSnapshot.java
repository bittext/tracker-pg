package com.svp.tracker.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "rh_predict_snapshot")
@Getter
@Setter
@NoArgsConstructor
public class RhPredictSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt;

    @Column(name = "open_value", precision = 19, scale = 6)
    private BigDecimal openValue;

    @Column(name = "realized_all", nullable = false, precision = 19, scale = 6)
    private BigDecimal realizedAll = BigDecimal.ZERO;

    @Column(name = "realized_week", nullable = false, precision = 19, scale = 6)
    private BigDecimal realizedWeek = BigDecimal.ZERO;

    @Column(name = "close_count", nullable = false)
    private int closeCount;

    @Column(name = "warnings", columnDefinition = "TEXT")
    private String warnings;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
