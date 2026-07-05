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
@Table(name = "rh_daily_tracker_alert_event")
@Getter
@Setter
@NoArgsConstructor
public class RhDailyTrackerAlertEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "account_suffix", nullable = false, length = 8)
    private String accountSuffix;

    @Column(name = "snapshot_id")
    private Long snapshotId;

    @Column(name = "prior_snapshot_id")
    private Long priorSnapshotId;

    @Column(name = "trigger_reasons", nullable = false, length = 128)
    private String triggerReasons;

    @Column(name = "delta_dollars", precision = 19, scale = 2)
    private BigDecimal deltaDollars;

    @Column(name = "delta_percent", precision = 8, scale = 4)
    private BigDecimal deltaPercent;

    @Column(name = "email_status", nullable = false, length = 16)
    private String emailStatus;

    @Column(name = "destination_masked", columnDefinition = "TEXT")
    private String destinationMasked;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
