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

    /** Cash-flow / holdings summary window for Finance → RH Accounts Track (default Apr 5 2026 Central). */
    @Column(name = "rh_accounts_track_started_at")
    private Instant rhAccountsTrackStartedAt;

    @Column(name = "individual_account_suffix", nullable = false, length = 8)
    private String individualAccountSuffix = "3370";

    @Column(name = "individual_baseline_nbis", nullable = false, precision = 19, scale = 6)
    private BigDecimal individualBaselineNbis;

    @Column(name = "agentic_account_suffix", nullable = false, length = 8)
    private String agenticAccountSuffix = "3550";

    @Column(name = "agentic_baseline_market_value", precision = 19, scale = 2)
    private BigDecimal agenticBaselineMarketValue;

    /** Portfolio total at {@link #rhAccountsTrackStartedAt} (individual ••••3370). */
    @Column(name = "individual_starting_total_value", precision = 19, scale = 2)
    private BigDecimal individualStartingTotalValue;

    /** Portfolio total at RH Accounts Track cutoff (Agentic account; usually $0 before first funding). */
    @Column(name = "agentic_starting_total_value", precision = 19, scale = 2)
    private BigDecimal agenticStartingTotalValue;

    @Column(name = "managed_account_suffix", length = 8)
    private String managedAccountSuffix = "4123";

    @Column(name = "managed_starting_total_value", precision = 19, scale = 2)
    private BigDecimal managedStartingTotalValue;

    /** Comma-separated last-4 account suffixes hidden from Daily Tracker (other synced RH accounts). */
    @Column(name = "excluded_account_suffixes", nullable = false, columnDefinition = "TEXT")
    private String excludedAccountSuffixes = "";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
