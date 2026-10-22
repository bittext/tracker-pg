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
        name = "rh_predict_daily",
        uniqueConstraints = @UniqueConstraint(columnNames = {"owner_user_id", "snapshot_date"}))
@Getter
@Setter
@NoArgsConstructor
public class RhPredictDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "closes", nullable = false)
    private int closes;

    @Column(name = "invested", nullable = false, precision = 19, scale = 6)
    private BigDecimal invested = BigDecimal.ZERO;

    @Column(name = "payback", nullable = false, precision = 19, scale = 6)
    private BigDecimal payback = BigDecimal.ZERO;

    @Column(name = "returned", nullable = false, precision = 19, scale = 6)
    private BigDecimal returned = BigDecimal.ZERO;

    @Column(name = "open_value", precision = 19, scale = 6)
    private BigDecimal openValue;

    @Column(name = "open_cost", precision = 19, scale = 6)
    private BigDecimal openCost;

    /** True when written by the 9 PM CT end-of-day job (includes open mark). */
    @Column(name = "official", nullable = false)
    private boolean official;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
