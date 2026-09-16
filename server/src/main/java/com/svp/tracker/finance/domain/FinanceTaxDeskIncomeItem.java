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
@Table(name = "finance_tax_desk_income_items")
@Getter
@Setter
@NoArgsConstructor
public class FinanceTaxDeskIncomeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "tax_year", nullable = false)
    private int taxYear;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String kind = "W2";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payer = "";

    @Column(name = "ytd_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal ytdAmount = BigDecimal.ZERO;

    @Column(name = "annual_projected", nullable = false, precision = 19, scale = 2)
    private BigDecimal annualProjected = BigDecimal.ZERO;

    @Column(name = "withholding_ytd", nullable = false, precision = 19, scale = 2)
    private BigDecimal withholdingYtd = BigDecimal.ZERO;

    @Column(name = "withholding_annual_projected", nullable = false, precision = 19, scale = 2)
    private BigDecimal withholdingAnnualProjected = BigDecimal.ZERO;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String notes = "";

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
