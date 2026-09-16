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
        name = "finance_tax_desk_daily_snapshots",
        uniqueConstraints = @UniqueConstraint(columnNames = {"owner_user_id", "tax_year", "as_of_date"}))
@Getter
@Setter
@NoArgsConstructor
public class FinanceTaxDeskDailySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "tax_year", nullable = false)
    private int taxYear;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Column(name = "risk_level", nullable = false, columnDefinition = "TEXT")
    private String riskLevel = "CATCH_UP";

    @Column(name = "estimated_tax", nullable = false, precision = 19, scale = 2)
    private BigDecimal estimatedTax = BigDecimal.ZERO;

    @Column(name = "filing_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal filingBalance = BigDecimal.ZERO;

    @Column(name = "penalty_exposure", nullable = false, precision = 19, scale = 2)
    private BigDecimal penaltyExposure = BigDecimal.ZERO;

    @Column(name = "realized_ytd", nullable = false, precision = 19, scale = 2)
    private BigDecimal realizedYtd = BigDecimal.ZERO;

    @Column(name = "workbook_json", nullable = false, columnDefinition = "TEXT")
    private String workbookJson = "{}";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
