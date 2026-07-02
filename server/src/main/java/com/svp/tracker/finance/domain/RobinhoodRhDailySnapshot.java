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
@Table(name = "robinhood_rh_daily_snapshot")
@Getter
@Setter
@NoArgsConstructor
public class RobinhoodRhDailySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "snapshot_at", nullable = false)
    private Instant snapshotAt;

    /** Central calendar date for the 9 PM snapshot boundary. */
    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    /** SCHEDULED = daily 9 PM job; MANUAL = ad-hoc Capture now. */
    @Column(name = "capture_kind", nullable = false, length = 16)
    private String captureKind = RobinhoodRhDailyCaptureKind.SCHEDULED;

    @Column(name = "account_suffix", nullable = false, length = 8)
    private String accountSuffix;

    @Column(name = "account_number", length = 32)
    private String accountNumber;

    @Column(name = "label", nullable = false, length = 128)
    private String label;

    @Column(name = "account_kind", nullable = false, length = 16)
    private String accountKind;

    @Column(name = "total_account_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAccountValue;

    @Column(name = "cash_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal cashBalance;

    @Column(name = "equity_market_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal equityMarketValue;

    @Column(name = "period_added", nullable = false, precision = 19, scale = 2)
    private BigDecimal periodAdded = BigDecimal.ZERO;

    @Column(name = "period_removed", nullable = false, precision = 19, scale = 2)
    private BigDecimal periodRemoved = BigDecimal.ZERO;

    @Column(name = "period_value_change", nullable = false, precision = 19, scale = 2)
    private BigDecimal periodValueChange = BigDecimal.ZERO;

    @Column(name = "period_start_date")
    private LocalDate periodStartDate;

    @Column(name = "holdings_json", nullable = false, columnDefinition = "TEXT")
    private String holdingsJson = "[]";

    @Column(name = "flows_json", nullable = false, columnDefinition = "TEXT")
    private String flowsJson = "[]";

    /** Executed Robinhood trades during the snapshot period (JSON array of RobinhoodRhDailyTradeDto). */
    @Column(name = "trades_json", nullable = false, columnDefinition = "TEXT")
    private String tradesJson = "[]";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
