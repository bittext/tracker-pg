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
@Table(name = "rh_daily_tracker_account_alert")
@Getter
@Setter
@NoArgsConstructor
public class RhDailyTrackerAccountAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "account_suffix", nullable = false, length = 8)
    private String accountSuffix;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "value_dollars_enabled", nullable = false)
    private boolean valueDollarsEnabled;

    @Column(name = "min_value_change_dollars", precision = 19, scale = 2)
    private BigDecimal minValueChangeDollars;

    @Column(name = "value_percent_enabled", nullable = false)
    private boolean valuePercentEnabled;

    @Column(name = "min_value_change_percent", precision = 8, scale = 4)
    private BigDecimal minValueChangePercent;

    @Column(name = "position_change_enabled", nullable = false)
    private boolean positionChangeEnabled;

    @Column(name = "cooldown_minutes", nullable = false)
    private int cooldownMinutes = 60;

    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

    @Column(name = "last_triggered_snapshot_id")
    private Long lastTriggeredSnapshotId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
