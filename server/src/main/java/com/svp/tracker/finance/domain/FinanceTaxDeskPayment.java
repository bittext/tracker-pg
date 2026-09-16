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
@Table(name = "finance_tax_desk_payments")
@Getter
@Setter
@NoArgsConstructor
public class FinanceTaxDeskPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "tax_year", nullable = false)
    private int taxYear;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String method = "";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String source = "MANUAL";

    @Column(name = "source_ref", nullable = false, columnDefinition = "TEXT")
    private String sourceRef = "";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String notes = "";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
