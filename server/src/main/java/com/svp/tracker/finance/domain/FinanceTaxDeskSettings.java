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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "finance_tax_desk_settings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"owner_user_id", "tax_year"}))
@Getter
@Setter
@NoArgsConstructor
public class FinanceTaxDeskSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "tax_year", nullable = false)
    private int taxYear;

    @Column(name = "filing_status", nullable = false, columnDefinition = "TEXT")
    private String filingStatus = "MARRIED_FILING_JOINTLY";

    @Column(name = "resident_state", nullable = false, columnDefinition = "TEXT")
    private String residentState = "TX";

    @Column(name = "short_term_loss_carryover", nullable = false, precision = 19, scale = 2)
    private BigDecimal shortTermLossCarryover = BigDecimal.ZERO;

    @Column(name = "long_term_loss_carryover", nullable = false, precision = 19, scale = 2)
    private BigDecimal longTermLossCarryover = BigDecimal.ZERO;

    @Column(name = "prior_year_agi", nullable = false, precision = 19, scale = 2)
    private BigDecimal priorYearAgi = BigDecimal.ZERO;

    @Column(name = "prior_year_tax", nullable = false, precision = 19, scale = 2)
    private BigDecimal priorYearTax = BigDecimal.ZERO;

    @Column(name = "child_tax_credit", nullable = false, precision = 19, scale = 2)
    private BigDecimal childTaxCredit = BigDecimal.ZERO;

    @Column(name = "target_refund", nullable = false, precision = 19, scale = 2)
    private BigDecimal targetRefund = new BigDecimal("5000.00");

    @Column(nullable = false, columnDefinition = "TEXT")
    private String notes = "";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
