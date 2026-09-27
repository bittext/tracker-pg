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
@Table(name = "rh_individual_margin_peek")
@Getter
@Setter
@NoArgsConstructor
public class RhIndividualMarginPeek {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "account_suffix", nullable = false, length = 8)
    private String accountSuffix = "3370";

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "capture_kind", nullable = false, length = 16)
    private String captureKind;

    @Column(name = "cash_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal cashBalance;

    @Column(name = "equity_market_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal equityMarketValue;

    @Column(name = "portfolio_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal portfolioValue;

    @Column(name = "options_value", precision = 19, scale = 2)
    private BigDecimal optionsValue;

    @Column(name = "buying_power", precision = 19, scale = 2)
    private BigDecimal buyingPower;

    @Column(name = "unleveraged_buying_power", precision = 19, scale = 2)
    private BigDecimal unleveragedBuyingPower;

    @Column(name = "margin_debit", nullable = false, precision = 19, scale = 2)
    private BigDecimal marginDebit;

    @Column(name = "borrow_percent", nullable = false, precision = 8, scale = 2)
    private BigDecimal borrowPercent;

    @Column(name = "annual_rate_percent", nullable = false, precision = 8, scale = 4)
    private BigDecimal annualRatePercent;

    @Column(name = "daily_interest", nullable = false, precision = 19, scale = 2)
    private BigDecimal dailyInterest;

    @Column(name = "maintenance_requirement", precision = 19, scale = 2)
    private BigDecimal maintenanceRequirement;

    @Column(name = "maintenance_source", length = 16)
    private String maintenanceSource;

    @Column(name = "buffer_amount", precision = 19, scale = 2)
    private BigDecimal bufferAmount;

    @Column(name = "buffer_percent", precision = 8, scale = 2)
    private BigDecimal bufferPercent;

    @Column(name = "near_call", nullable = false)
    private boolean nearCall;

    @Column(name = "high_borrow", nullable = false)
    private boolean highBorrow;

    @Column(name = "risk_status", nullable = false, length = 16)
    private String riskStatus = "UNKNOWN";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
