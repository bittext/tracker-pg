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
@Table(name = "robinhood_account_tracker_config")
@Getter
@Setter
@NoArgsConstructor
public class RobinhoodAccountTrackerConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false, unique = true)
    private Long ownerUserId;

    @Column(name = "tracking_started_at", nullable = false)
    private Instant trackingStartedAt;

    @Column(name = "individual_account_suffix", nullable = false, length = 8)
    private String individualAccountSuffix = "3370";

    @Column(name = "individual_baseline_nbis", nullable = false, precision = 19, scale = 6)
    private BigDecimal individualBaselineNbis;

    @Column(name = "agentic_account_suffix", nullable = false, length = 8)
    private String agenticAccountSuffix = "3550";

    @Column(name = "agentic_baseline_market_value", precision = 19, scale = 2)
    private BigDecimal agenticBaselineMarketValue;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
